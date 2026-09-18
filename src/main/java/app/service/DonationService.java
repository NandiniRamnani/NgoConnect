package app.service;

import app.dto.CreateOrderResponse;
import app.dto.DonationRequest;
import app.dto.VerifyPaymentRequest;
import app.enums.DonationStatus;
import app.enums.PaymentMode;
import app.model.Account;
import app.model.Donation;
import app.model.Ngo;
import app.model.WalletTransaction;
import app.repository.AccountRepository;
import app.repository.DonationRepository;
import app.repository.NgoRepository;
import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DonationService {

    /**
     * Biggest single gateway donation. Razorpay itself refuses very large one-shot payments, and
     * an unbounded amount would overflow the int that Checkout expects paise in.
     */
    private static final long MAX_DONATION_PAISE = 100_000_000L; // Rs.10,00,000

    private final DonationRepository donationRepository;
    private final AccountRepository accountRepository;
    private final NgoRepository ngoRepository;
    private final RazorpayClient razorpayClient;

    /**
     * Needed for the same reason WalletService needs it: a repository save writes the WHOLE
     * document, which cannot express "change this row only if it is still PENDING_PAYMENT".
     * That condition is what makes verifyPayment safe to call twice — see the claim in it.
     */
    private final MongoTemplate mongoTemplate;

    /** Draws receipt numbers and renders the 80G PDF. */
    private final ReceiptService receiptService;

    /** Delivers that PDF to the donor's inbox. Best-effort — see emailReceipt. */
    private final MailService mailService;

    /**
     * Donations can now be funded two ways, and this service owns both. The Razorpay path is
     * unchanged below; the wallet path delegates every balance change to WalletService, which
     * is the only class permitted to touch a balance. The dependency points one way only
     * (donations know about wallets, wallets know nothing about donations), so there is no
     * circular bean reference for Spring to trip over.
     */
    private final WalletService walletService;

    /**
     * Recording a donation is only half the job — the NGO has to actually be owed the money.
     * This credits their balance whenever a donation reaches PAID, by either funding route.
     * The dependency runs one way (donations -> balances), so there is no bean cycle.
     */
    private final NgoBalanceService ngoBalanceService;

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    /**
     * When true, the two calls that leave this machine are skipped and the payment is simulated.
     * See the note in application.properties for exactly what is and is not bypassed.
     */
    @Value("${payments.demo-mode:false}")
    private boolean demoMode;

    /**
     * Marks an order id as locally invented rather than issued by Razorpay. Carrying the fact in
     * the id itself means any record can be identified as a simulation forever after, from the
     * database alone, without needing to know how the server was configured that day.
     */
    static final String DEMO_ORDER_PREFIX = "order_demo_";

    public DonationService(DonationRepository donationRepository, AccountRepository accountRepository,
                           NgoRepository ngoRepository, RazorpayClient razorpayClient,
                           WalletService walletService, NgoBalanceService ngoBalanceService,
                           MongoTemplate mongoTemplate, ReceiptService receiptService,
                           MailService mailService) {
        this.receiptService = receiptService;
        this.mailService = mailService;
        this.donationRepository = donationRepository;
        this.accountRepository = accountRepository;
        this.ngoRepository = ngoRepository;
        this.razorpayClient = razorpayClient;
        this.walletService = walletService;
        this.ngoBalanceService = ngoBalanceService;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Step 1 of the payment flow: create a PENDING_PAYMENT donation record, then ask Razorpay
     * for an Order against it. The frontend uses the returned order id to open Checkout — the
     * donor hasn't paid anything yet, this just reserves the transaction on Razorpay's side.
     */
    public CreateOrderResponse createOrder(String donorEmail, DonationRequest request) {
        Account donor = accountRepository.findByEmail(donorEmail);
        if (donor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account required");
        if (!ngoRepository.existsById(request.getNgoId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "NGO not found");

        // One shared converter for both funding routes, so an amount accepted by the wallet is
        // accepted here too. It rejects null, zero, negative and sub-paisa amounts with a 400 —
        // the old inline multiply().intValueExact() threw ArithmeticException on something as
        // ordinary as Rs.100.555 and surfaced it to the donor as a 500.
        long amountPaise = WalletService.toPaise(request.getAmount());
        if (amountPaise > MAX_DONATION_PAISE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum single donation is Rs.10,00,000");

        Donation donation = new Donation();
        donation.setNgoId(request.getNgoId());
        donation.setAmount(request.getAmount());
        donation.setUserId(donor.getId());
        donation.setStatus(DonationStatus.PENDING_PAYMENT);
        donation.setCreatedAt(Instant.now());
        donation.setPaymentReference("DON-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        donation = donationRepository.save(donation);

        // Razorpay takes amounts in paise (smallest INR unit) as a whole number — ₹500.00 becomes 50000.
        // Safe to narrow: MAX_DONATION_PAISE is well inside int range.
        int amountInPaise = (int) amountPaise;

        /*
         * DEMO MODE: mint the order id here instead of asking Razorpay for one.
         *
         * This is the only difference on this path. The donation row above was already written
         * exactly as it always is, and step 2 will claim it, credit the NGO and issue an 80G
         * receipt exactly as it always does — so everything the system actually does is still
         * being demonstrated. What is skipped is the network call to a third party, which is the
         * one part of the flow that can fail for reasons that have nothing to do with this code.
         */
        if (demoMode) {
            String demoOrderId = DEMO_ORDER_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            donation.setRazorpayOrderId(demoOrderId);
            donationRepository.save(donation);
            return CreateOrderResponse.demo(donation.getId(), demoOrderId, amountInPaise);
        }

        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", donation.getId()); // lets us cross-reference this order in the Razorpay dashboard
            Order order = razorpayClient.orders.create(orderRequest);
            String razorpayOrderId = order.get("id");

            donation.setRazorpayOrderId(razorpayOrderId);
            donationRepository.save(donation);

            return new CreateOrderResponse(donation.getId(), razorpayOrderId, amountInPaise, "INR", keyId);
        } catch (RazorpayException e) {
            donationRepository.deleteById(donation.getId()); // don't leave an orphaned record with no order behind it
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not start payment: " + e.getMessage());
        }
    }

    /**
     * Step 2: the frontend calls this once Razorpay Checkout reports success, handing back the
     * three values Razorpay signed. We recompute that signature ourselves — this is the only
     * proof that the payment is genuine, since everything up to this point happened in the
     * user's browser and could theoretically have been faked.
     */
    public Donation verifyPayment(String donorEmail, VerifyPaymentRequest request) {
        Account donor = accountRepository.findByEmail(donorEmail);
        if (donor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account required");

        Donation donation = donationRepository.findByRazorpayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Donation not found"));
        if (!donation.getUserId().equals(donor.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This donation doesn't belong to you");

        // Already verified and credited — a double click, a retry after a flaky response, a
        // refreshed tab. Report the existing donation instead of running creditNgo() again, which
        // would add the same rupees to the NGO's balance a second time out of nothing.
        if (donation.getStatus() == DonationStatus.PAID) return donation;
        if (donation.getStatus() == DonationStatus.FAILED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This payment already failed");

        JSONObject signatureCheck = new JSONObject();
        signatureCheck.put("razorpay_order_id", request.getRazorpayOrderId());
        signatureCheck.put("razorpay_payment_id", request.getRazorpayPaymentId());
        signatureCheck.put("razorpay_signature", request.getRazorpaySignature());

        boolean valid;
        if (isDemoOrder(donation.getRazorpayOrderId())) {
            /*
             * DEMO MODE: there is no gateway, so there is no signature for anyone to have produced.
             * Accepting the payment is the only possible outcome.
             *
             * The condition is the ORDER ID, not the demoMode flag. That distinction matters: a
             * server switched back to real payments must not then accept unsigned verification of
             * a genuine Razorpay order just because a stale demo request arrived. Tying it to the
             * "order_demo_" prefix means only records created as simulations can ever be completed
             * as simulations, and a real order always requires a real signature.
             */
            valid = true;
        } else try {
            valid = Utils.verifyPaymentSignature(signatureCheck, keySecret);
        } catch (RazorpayException e) {
            valid = false;
        }

        if (!valid) {
            // Conditional, not a whole-document save: if a concurrent request already verified and
            // paid this donation, writing the stale in-memory object back would mark a genuinely
            // PAID donation as FAILED while the NGO keeps the credit.
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(donation.getId())
                            .and("status").is(DonationStatus.PENDING_PAYMENT.name())),
                    new Update().set("status", DonationStatus.FAILED.name()),
                    Donation.class);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment could not be verified");
        }

        Donation claimed = claimAndCredit(donation, request.getRazorpayPaymentId(),
                fetchPaymentMode(request.getRazorpayPaymentId()), donor.getFullName());

        // null means the webhook, or another tab, won the race and already credited the NGO.
        return claimed != null ? claimed : donationRepository.findById(donation.getId()).orElse(donation);
    }

    // -- Gateway donation completion, shared by the browser and the webhook -----

    /**
     * Flip a PENDING_PAYMENT donation to PAID and credit the receiving NGO — the only place a
     * gateway donation is ever completed. Returns the updated donation, or null if the row was no
     * longer PENDING_PAYMENT, meaning somebody else already completed it.
     *
     * The claim is what makes this safe to call from two places at once. Checkout's success
     * callback and Razorpay's webhook are genuinely concurrent, and Razorpay re-sends a webhook it
     * believes failed, so this WILL be entered more than once for a single payment. The
     * findAndModify only matches while the status is still PENDING_PAYMENT: the first caller flips
     * it and gets the row, the rest match nothing and get null. Exactly one reaches creditNgo, so
     * the NGO's balance grows by the donation once rather than once per delivery.
     *
     * The caller must have already proven the payment is genuine — Checkout signature or webhook
     * signature. Nothing is verified here.
     */
    private Donation claimAndCredit(Donation donation, String razorpayPaymentId,
                                    PaymentMode mode, String donorName) {
        Donation claimed = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(donation.getId())
                        .and("status").is(DonationStatus.PENDING_PAYMENT.name())),
                new Update()
                        .set("status", DonationStatus.PAID.name())
                        .set("paymentReference", razorpayPaymentId)
                        .set("paymentMode", mode.name()),
                FindAndModifyOptions.options().returnNew(true),
                Donation.class);

        if (claimed == null) return null;

        /*
         * Assign the 80G receipt number only now, AFTER winning the claim, never before it.
         *
         * Receipt numbers must run unbroken — an auditor reads a missing number as a receipt that
         * was issued and hidden. If the number were drawn before the findAndModify, every caller
         * that lost the race would take a number and then discard it, leaving permanent gaps in
         * the sequence. Drawing it here means exactly one number is spent per donation.
         */
        String receiptNumber = receiptService.nextReceiptNumber(Instant.now());
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(claimed.getId())),
                new Update().set("receiptNumber", receiptNumber),
                Donation.class);
        claimed.setReceiptNumber(receiptNumber);

        // The NGO is now owed this money. It lands in their `clearing` bucket, not `available` —
        // Razorpay has not settled it to our bank yet, so it must not be withdrawable today.
        creditNgo(claimed, donorName);
        emailReceipt(claimed, donorName);
        return claimed;
    }

    /**
     * The webhook's way in: complete a donation Razorpay has confirmed it captured, with no
     * browser and no logged-in user involved. The counterpart to
     * {@link WalletService#completeTopUpFromWebhook}, and it exists for the same reason — a donor
     * whose browser never made it back to /verify has still paid, and the NGO is still owed.
     *
     * @return true if this call is what actually completed the donation, false if there was
     *         nothing to do — unknown order, already settled, or a mismatched amount.
     */
    public boolean completeDonationFromWebhook(String razorpayOrderId, String razorpayPaymentId,
                                               String method, long paidAmountPaise) {
        Donation donation = donationRepository.findByRazorpayOrderId(razorpayOrderId).orElse(null);
        if (donation == null) return false;                                  // not a donation order
        if (donation.getStatus() != DonationStatus.PENDING_PAYMENT) return false; // already settled

        // Refuse to record a donation for an amount Razorpay did not actually capture — the NGO's
        // balance would then be owed money that never arrived. Left PENDING_PAYMENT on purpose so
        // the mismatch stays visible instead of being quietly written off.
        long expectedPaise = WalletService.toPaise(donation.getAmount());
        if (expectedPaise != paidAmountPaise) {
            System.err.println("Webhook amount mismatch for donation order " + razorpayOrderId
                    + ": record says " + expectedPaise + " paise, Razorpay captured " + paidAmountPaise
                    + ". Not crediting.");
            return false;
        }

        // No session to read the donor's name from, so fetch it the long way for the ledger label.
        String donorName = accountRepository.findById(donation.getUserId())
                .map(Account::getFullName).orElse(null);

        return claimAndCredit(donation, razorpayPaymentId, toPaymentMode(method), donorName) != null;
    }

    /**
     * The wallet-funded alternative to createOrder + verifyPayment.
     *
     * The whole Razorpay round trip disappears here: the money is already inside NGOConnect
     * because the donor topped up earlier, so this is a single request that finishes in one
     * step instead of two. There is no signature to check, because no outside party is
     * involved and nothing untrusted from the browser decides how much moves — the amount is
     * validated server-side and the identity comes from the authenticated session.
     *
     * ORDER OF OPERATIONS, AND WHY
     *   1. Save the donation as PENDING_PAYMENT first, so the debit has a real donationId to
     *      point at. A ledger row that says "donation" without saying which one is useless
     *      during a dispute.
     *   2. Debit the wallet. This can fail on insufficient balance, and if it does we have
     *      only written a harmless pending record — nothing to unwind.
     *   3. Mark the donation PAID. If anything goes wrong at this last step the money is
     *      already gone from the balance, so the catch block refunds it. Taking money and
     *      failing to record what it bought is the one outcome we must never leave behind.
     */
    public Donation donateFromWallet(String donorEmail, DonationRequest request) {
        Account donor = accountRepository.findByEmail(donorEmail);
        if (donor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account required");

        Ngo ngo = ngoRepository.findById(request.getNgoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NGO not found"));

        // Same rupees-to-paise conversion and validation the top-up uses, so an amount that is
        // acceptable in one flow is acceptable in the other.
        long amountPaise = WalletService.toPaise(request.getAmount());

        Donation donation = new Donation();
        donation.setNgoId(request.getNgoId());
        donation.setAmount(request.getAmount());
        donation.setUserId(donor.getId());
        donation.setStatus(DonationStatus.PENDING_PAYMENT);
        donation.setPaymentMode(PaymentMode.WALLET_BALANCE);
        donation.setCreatedAt(Instant.now());
        donation.setPaymentReference("DON-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        donation = donationRepository.save(donation);

        // Throws 400 and leaves the balance untouched if the wallet is short. The record written
        // a moment ago must still be closed out — left as PENDING_PAYMENT it would sit in the
        // donor's history forever, looking like a donation that is about to go through.
        WalletTransaction walletTxn;
        try {
            walletTxn = walletService.debitForDonation(donor.getId(), amountPaise, donation.getId(), ngo.getNgoName());
        } catch (RuntimeException e) {
            donation.setStatus(DonationStatus.FAILED);
            donationRepository.save(donation);
            throw e;
        }

        try {
            donation.setStatus(DonationStatus.PAID);
            // The ledger row id plays the role a Razorpay payment id plays in the gateway flow:
            // the receipt that proves where this donation's money came from.
            donation.setPaymentReference(walletTxn.getId());
            // No claim needed on this path: this request created the donation moments ago and is
            // the only thing that can complete it, so there is no race to lose and no risk of two
            // numbers being drawn for one donation.
            donation.setReceiptNumber(receiptService.nextReceiptNumber(Instant.now()));
            Donation saved = donationRepository.save(donation);
            creditNgo(saved, donor.getFullName());
            emailReceipt(saved, donor.getFullName());
            return saved;
        } catch (RuntimeException e) {
            walletService.refundDonation(donor.getId(), amountPaise, donation.getId());
            donation.setStatus(DonationStatus.FAILED);
            donationRepository.save(donation);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not complete the donation. Your wallet has been refunded.");
        }
    }

    /**
     * Push a paid donation into the receiving NGO's balance.
     *
     * Deliberately best-effort: the donor has already parted with their money, so a failure here
     * must not turn a successful donation into an error on their screen. The Donation record is
     * the source of truth, so a missed credit can always be replayed from it.
     */
    private void creditNgo(Donation donation, String donorName) {
        try {
            long amountPaise = WalletService.toPaise(donation.getAmount());
            ngoBalanceService.creditFromDonation(donation.getNgoId(), amountPaise,
                    donation.getId(), donorName);
        } catch (RuntimeException e) {
            System.err.println("Could not credit NGO " + donation.getNgoId()
                    + " for donation " + donation.getId() + ": " + e.getMessage());
        }
    }

    /** True for an order this server invented rather than one Razorpay issued. */
    static boolean isDemoOrder(String razorpayOrderId) {
        return razorpayOrderId != null && razorpayOrderId.startsWith(DEMO_ORDER_PREFIX);
    }

    /** Best-effort — if this lookup fails the donation is still genuinely paid, we just won't know the method. */
    private PaymentMode fetchPaymentMode(String razorpayPaymentId) {
        // A simulated payment has no record at Razorpay to look up, and asking would just cost a
        // failed network call before falling through to the CARD default anyway.
        if (razorpayPaymentId != null && razorpayPaymentId.startsWith("pay_demo_")) return PaymentMode.UPI;
        try {
            Payment payment = razorpayClient.payments.fetch(razorpayPaymentId);
            return toPaymentMode(payment.get("method"));
        } catch (Exception e) {
            return PaymentMode.CARD;
        }
    }

    /**
     * Razorpay's method string to our enum. Split out from fetchPaymentMode because the webhook
     * already has the method in its payload and has no reason to make a second call over the
     * network to learn something it was just told.
     */
    private PaymentMode toPaymentMode(String method) {
        if (method == null) return PaymentMode.CARD;
        return switch (method) {
            case "upi" -> PaymentMode.UPI;
            case "netbanking" -> PaymentMode.NET_BANKING;
            case "wallet" -> PaymentMode.WALLET;
            default -> PaymentMode.CARD;
        };
    }

    // -- 80G receipts ----------------------------------------------------------

    /**
     * Build the donor's tax receipt on demand, as PDF bytes.
     *
     * Regenerated from the stored donation every time rather than kept on disk. The receipt number
     * and every figure on the page come from data that never changes after payment, so two
     * downloads a year apart produce the identical document — which is the property that matters
     * for something a donor may have already filed.
     *
     * The ownership check is the important line here. Donation ids are guessable, and a receipt
     * carries the donor's name and email alongside the amount they gave; without this, changing a
     * digit in the URL would hand you someone else's.
     */
    public byte[] receiptPdf(String donorEmail, String donationId) {
        Account donor = accountRepository.findByEmail(donorEmail);
        if (donor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account required");

        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Donation not found"));

        if (!donation.getUserId().equals(donor.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This donation doesn't belong to you");

        // A receipt certifies money actually received. Issuing one for a pending or failed
        // donation would be certifying a payment that never happened.
        if (donation.getStatus() != DonationStatus.PAID)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A receipt is only available once the donation is paid");

        Ngo ngo = ngoRepository.findById(donation.getNgoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NGO not found"));

        return receiptService.generatePdf(donation, donor.getFullName(), donor.getEmail(), ngo);
    }

    /**
     * Email the receipt the moment the donation succeeds, so the donor has it without having to
     * come back and look for it.
     *
     * Best-effort by design, exactly like creditNgo: the donation is already complete and the money
     * already moved, so a mail server that is slow, misconfigured, or missing its credentials must
     * not turn a successful donation into an error on the donor's screen. The receipt stays
     * downloadable from their dashboard regardless, so a failure here costs convenience, not the
     * document.
     */
    private void emailReceipt(Donation donation, String donorName) {
        try {
            Account donor = accountRepository.findById(donation.getUserId()).orElse(null);
            Ngo ngo = ngoRepository.findById(donation.getNgoId()).orElse(null);
            if (donor == null || ngo == null || donor.getEmail() == null) return;

            byte[] pdf = receiptService.generatePdf(donation, donorName, donor.getEmail(), ngo);

            String body = """
                    Dear %s,

                    Thank you for your donation of Rs. %s to %s.

                    Your 80G donation receipt is attached, receipt number %s. Please keep it for
                    your income tax records.

                    You can download it again any time from your NGOConnect dashboard.

                    — NGOConnect
                    """.formatted(donorName == null ? "Donor" : donorName,
                    donation.getAmount(), ngo.getNgoName(), donation.getReceiptNumber());

            mailService.sendWithAttachment(donor.getEmail(),
                    "Your donation receipt " + donation.getReceiptNumber(),
                    body,
                    "receipt-" + donation.getReceiptNumber().replace('/', '-') + ".pdf",
                    pdf);

        } catch (RuntimeException e) {
            System.err.println("Could not email receipt for donation " + donation.getId() + ": " + e.getMessage());
        }
    }

    public List<Donation> findForUser(String donorEmail) {
        Account donor = accountRepository.findByEmail(donorEmail);
        if (donor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account required");
        return donationRepository.findByUserIdOrderByCreatedAtDesc(donor.getId());
    }
}
