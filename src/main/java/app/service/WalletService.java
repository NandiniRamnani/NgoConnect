package app.service;

import app.dto.TopUpOrderResponse;
import app.dto.TopUpRequest;
import app.dto.VerifyPaymentRequest;
import app.enums.WalletTransactionStatus;
import app.enums.WalletTransactionType;
import app.model.Account;
import app.model.Wallet;
import app.model.WalletTransaction;
import app.repository.AccountRepository;
import app.repository.WalletTransactionRepository;
import com.razorpay.Order;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The single owner of every rupee inside NGOConnect wallets. Nothing else in the codebase is
 * allowed to change a balance — DonationService asks this class to do it. Keeping all balance
 * arithmetic in one file is what makes the invariant "balance never goes negative and always
 * equals the sum of the ledger" possible to actually guarantee.
 *
 * TWO KINDS OF MONEY MOVEMENT LIVE HERE
 *   1. TOP-UP (outside money coming in): a real Razorpay payment, so it needs the full
 *      two-step create-order / verify-signature dance, exactly like a direct donation.
 *   2. DEBIT and REFUND (internal): no gateway, no network call — just an atomic number
 *      change in MongoDB plus a ledger row. This is why paying from balance feels instant.
 */
@Service
public class WalletService {

    /** Guard rails on a single top-up: nothing below Rs.1, nothing above Rs.1,00,000. */
    private static final long MIN_TOPUP_PAISE = 100L;
    private static final long MAX_TOPUP_PAISE = 10_000_000L;

    private final WalletTransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final RazorpayClient razorpayClient;

    /**
     * MongoTemplate is the lower-level Mongo API that MongoRepository is built on top of.
     * We need it because repositories can only save whole documents, and saving a whole
     * document is unsafe for money — see debit() below for the concurrency reason.
     */
    private final MongoTemplate mongoTemplate;

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    /** Simulate the gateway instead of calling it — see application.properties. */
    @Value("${payments.demo-mode:false}")
    private boolean demoMode;

    public WalletService(WalletTransactionRepository transactionRepository,
                         AccountRepository accountRepository,
                         RazorpayClient razorpayClient,
                         MongoTemplate mongoTemplate) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.razorpayClient = razorpayClient;
        this.mongoTemplate = mongoTemplate;
    }

    // -- Reading ---------------------------------------------------------------

    /**
     * Fetch a user's wallet, creating an empty one the first time they ask.
     *
     * The upsert matters. The naive version — findByUserId, and if empty then save a new one —
     * has a gap between the read and the write. Two browser tabs opening the wallet page at the
     * same instant would both see "no wallet" and both insert one. Here the whole find-or-insert
     * happens inside a single MongoDB command, so the second request finds the wallet the first
     * one just made.
     *
     * setOnInsert applies its fields ONLY when a new document is created, so an existing balance
     * can never be reset to zero by this call.
     */
    public Wallet getOrCreateWallet(String userId) {
        Instant now = Instant.now();
        return mongoTemplate.findAndModify(
                Query.query(Criteria.where("userId").is(userId)),
                new Update()
                        .setOnInsert("userId", userId)
                        .setOnInsert("balancePaise", 0L)
                        .setOnInsert("createdAt", now)
                        .set("updatedAt", now),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                Wallet.class);
    }

    /** Same as above, starting from the logged-in email Spring Security hands the controller. */
    public Wallet getWalletForEmail(String email) {
        return getOrCreateWallet(requireAccount(email).getId());
    }

    public List<WalletTransaction> getTransactionsForEmail(String email) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(requireAccount(email).getId());
    }

    // -- Top-up step 1: reserve a Razorpay order -------------------------------

    /**
     * Creates a PENDING ledger row, then asks Razorpay for an Order to attach to it.
     *
     * NO MONEY MOVES HERE. The row is a placeholder meaning "this user intends to add Rs.X";
     * the balance itself is untouched until a verified signature arrives in step 2.
     *
     * Writing our row BEFORE calling Razorpay is deliberate: if we called Razorpay first and
     * then crashed, there would be a live order at the gateway that our database knows nothing
     * about, and a paying user with no row to credit.
     */
    public TopUpOrderResponse createTopUpOrder(String email, TopUpRequest request) {
        Account user = requireAccount(email);
        long amountPaise = toPaise(request.getAmount());

        if (amountPaise < MIN_TOPUP_PAISE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Minimum top-up is Rs.1");
        if (amountPaise > MAX_TOPUP_PAISE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum top-up is Rs.1,00,000");

        getOrCreateWallet(user.getId()); // make sure a wallet exists to credit later

        WalletTransaction txn = new WalletTransaction();
        txn.setUserId(user.getId());
        txn.setType(WalletTransactionType.TOPUP);
        txn.setStatus(WalletTransactionStatus.PENDING);
        txn.setAmountPaise(amountPaise);
        txn.setDescription("Wallet top-up");
        txn.setCreatedAt(Instant.now());
        txn = transactionRepository.save(txn);

        // DEMO MODE: same reasoning as DonationService.createOrder. The PENDING ledger row above is
        // real, and step 2 will claim and credit it through the identical atomic path — only the
        // call to the gateway is replaced by a locally generated id.
        if (demoMode) {
            String demoOrderId = DonationService.DEMO_ORDER_PREFIX
                    + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            txn.setRazorpayOrderId(demoOrderId);
            transactionRepository.save(txn);
            return TopUpOrderResponse.demo(txn.getId(), demoOrderId, (int) amountPaise);
        }

        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", txn.getId()); // lets us match this order in the Razorpay dashboard
            Order order = razorpayClient.orders.create(orderRequest);

            txn.setRazorpayOrderId(order.get("id"));
            transactionRepository.save(txn);

            return new TopUpOrderResponse(txn.getId(), txn.getRazorpayOrderId(),
                    (int) amountPaise, "INR", keyId);
        } catch (RazorpayException e) {
            // No order exists at the gateway, so this row can never be completed — bin it rather
            // than leave a permanently PENDING entry cluttering the user's passbook.
            transactionRepository.deleteById(txn.getId());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not start wallet top-up: " + e.getMessage());
        }
    }

    // -- Top-up step 2: verify the signature, then credit ----------------------

    /**
     * Called after Razorpay Checkout reports success. Everything up to this point happened
     * inside the user's browser, which we cannot trust — anyone could call this endpoint by
     * hand with invented ids. The signature is the proof that they cannot forge.
     *
     * Razorpay computes HMAC-SHA256 of "orderId|paymentId" keyed with our key_secret and sends
     * the result as razorpay_signature. Only Razorpay and our server know key_secret, so
     * recomputing it and getting a match proves the payment is real and unmodified.
     */
    public Wallet verifyTopUp(String email, VerifyPaymentRequest request) {
        Account user = requireAccount(email);

        WalletTransaction txn = transactionRepository.findByRazorpayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Top-up not found"));
        if (!txn.getUserId().equals(user.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This top-up does not belong to you");

        // Already credited (double click, a retry, a refreshed tab) — report success without
        // adding the money a second time. An endpoint safe to call twice is called idempotent.
        if (txn.getStatus() == WalletTransactionStatus.COMPLETED)
            return getOrCreateWallet(user.getId());
        if (txn.getStatus() == WalletTransactionStatus.FAILED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This top-up already failed");

        JSONObject signatureCheck = new JSONObject();
        signatureCheck.put("razorpay_order_id", request.getRazorpayOrderId());
        signatureCheck.put("razorpay_payment_id", request.getRazorpayPaymentId());
        signatureCheck.put("razorpay_signature", request.getRazorpaySignature());

        boolean valid;
        if (DonationService.isDemoOrder(txn.getRazorpayOrderId())) {
            // DEMO MODE: nothing signed this, because nothing outside this machine was involved.
            // Keyed on the order id rather than the flag, so a server switched back to real
            // payments can never accept an unsigned verification for a genuine Razorpay order.
            valid = true;
        } else try {
            valid = Utils.verifyPaymentSignature(signatureCheck, keySecret);
        } catch (RazorpayException e) {
            valid = false;
        }

        if (!valid) {
            // Conditional, not a whole-document save: a save would write back the object as it was
            // read, so if a concurrent request had already credited this top-up in the meantime,
            // the row would be stamped FAILED while the money stayed in the balance.
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(txn.getId())
                            .and("status").is(WalletTransactionStatus.PENDING.name())),
                    new Update().set("status", WalletTransactionStatus.FAILED.name()),
                    WalletTransaction.class);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment could not be verified");
        }

        Wallet wallet = claimAndCredit(txn, request.getRazorpayPaymentId());

        // null means the webhook, or another tab, won the race and already credited this row.
        return wallet != null ? wallet : getOrCreateWallet(user.getId());
    }

    // -- Top-up completion, shared by the browser and the webhook ---------------

    /**
     * Flip a PENDING top-up to COMPLETED and move the money — the only place a top-up is ever
     * credited. Returns the new wallet, or null if the row was not PENDING any more, meaning
     * somebody else already credited it.
     *
     * WHY THIS IS ONE COMMAND, NOT AN IF FOLLOWED BY A WRITE
     * The findAndModify says "if and only if this row is still PENDING, flip it to COMPLETED, and
     * tell me whether you actually did it". Callers can arrive here simultaneously — the browser
     * finishing Checkout and Razorpay's webhook firing are genuinely concurrent, and Razorpay
     * retries a webhook it thinks failed. MongoDB serialises them on the document: the first
     * flips PENDING to COMPLETED and gets the row back, every other one matches nothing and gets
     * null. Only the winner reaches credit(), so the money lands exactly once no matter how many
     * times, or from how many directions, this is called.
     *
     * The caller must have already established that the payment is genuine — a valid Checkout
     * signature, or a valid webhook signature. This method does no verification of its own.
     */
    private Wallet claimAndCredit(WalletTransaction txn, String razorpayPaymentId) {
        WalletTransaction claimed = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(txn.getId())
                        .and("status").is(WalletTransactionStatus.PENDING.name())),
                new Update()
                        .set("status", WalletTransactionStatus.COMPLETED.name())
                        .set("razorpayPaymentId", razorpayPaymentId),
                FindAndModifyOptions.options().returnNew(true),
                WalletTransaction.class);

        if (claimed == null) return null;

        Wallet wallet = credit(txn.getUserId(), txn.getAmountPaise());

        // Stamp the passbook snapshot with a targeted field update rather than saving the whole
        // row back, so this write can only ever touch the one field it means to.
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(claimed.getId())),
                new Update().set("balanceAfterPaise", wallet.getBalancePaise()),
                WalletTransaction.class);
        return wallet;
    }

    /**
     * The webhook's way in: credit a top-up that Razorpay has confirmed it captured, with no
     * browser and no logged-in user involved.
     *
     * This exists because the browser is not a reliable reporter. A donor who pays and then closes
     * the tab, loses signal, or hits a failing network on the way back never sends the verify call
     * — their money is at Razorpay while our row sits PENDING forever. Razorpay tells us
     * server-to-server instead, and this credits from that.
     *
     * Identity is not checked here, and must not be: there is no session on a webhook. The row
     * itself records whose wallet it belongs to, and it was written by an authenticated user at
     * create-order time. Authenticity comes from the webhook signature the caller already checked.
     *
     * @return true if this call is what actually credited the wallet, false if there was nothing
     *         to do — unknown order, already settled, or an amount that did not match.
     */
    public boolean completeTopUpFromWebhook(String razorpayOrderId, String razorpayPaymentId, long paidAmountPaise) {
        WalletTransaction txn = transactionRepository.findByRazorpayOrderId(razorpayOrderId).orElse(null);
        if (txn == null) return false;                                   // not a wallet order
        if (txn.getStatus() != WalletTransactionStatus.PENDING) return false; // already settled

        /*
         * Credit what our own row says, and only once we have confirmed Razorpay captured that
         * exact figure. The amount in the webhook body is what the donor actually paid; the amount
         * on the row is what they asked to add. They can only disagree if something is wrong, so
         * the safe move is to credit nothing and leave the row PENDING for a human to look at —
         * crediting the larger of two numbers we do not trust is how a wallet leaks money.
         */
        if (txn.getAmountPaise() != paidAmountPaise) {
            System.err.println("Webhook amount mismatch for order " + razorpayOrderId
                    + ": row says " + txn.getAmountPaise() + " paise, Razorpay captured " + paidAmountPaise
                    + ". Not crediting.");
            return false;
        }

        return claimAndCredit(txn, razorpayPaymentId) != null;
    }

    // -- Internal money movement, used by DonationService ----------------------

    /**
     * Take money out of a wallet to pay for a donation, and write the matching ledger row.
     * Returns that row so the caller can store its id on the Donation as the payment reference —
     * the wallet equivalent of a Razorpay payment id.
     *
     * Throws 400 if the balance is too low. Note the check and the subtraction are the SAME
     * operation (see debit) rather than an if-statement followed by a write, which is what stops
     * a user with Rs.100 from spending it twice by firing two requests at once.
     */
    public WalletTransaction debitForDonation(String userId, long amountPaise, String donationId, String ngoName) {
        Wallet wallet = debit(userId, amountPaise);
        if (wallet == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Insufficient wallet balance. Please top up and try again.");

        WalletTransaction txn = new WalletTransaction();
        txn.setUserId(userId);
        txn.setType(WalletTransactionType.DONATION);
        txn.setStatus(WalletTransactionStatus.COMPLETED); // internal transfer — nothing to wait for
        txn.setAmountPaise(amountPaise);
        txn.setBalanceAfterPaise(wallet.getBalancePaise());
        txn.setDescription("Donation to " + ngoName);
        txn.setDonationId(donationId);
        txn.setCreatedAt(Instant.now());
        return transactionRepository.save(txn);
    }

    /**
     * Undo a debit when the donation could not be completed after the money was already taken.
     * Written as its own REFUND row rather than by deleting the DONATION row, because a ledger
     * you can edit is not a ledger — the history has to show what happened, including mistakes.
     */
    public WalletTransaction refundDonation(String userId, long amountPaise, String donationId) {
        Wallet wallet = credit(userId, amountPaise);

        WalletTransaction txn = new WalletTransaction();
        txn.setUserId(userId);
        txn.setType(WalletTransactionType.REFUND);
        txn.setStatus(WalletTransactionStatus.COMPLETED);
        txn.setAmountPaise(amountPaise);
        txn.setBalanceAfterPaise(wallet.getBalancePaise());
        txn.setDescription("Refund for failed donation");
        txn.setDonationId(donationId);
        txn.setCreatedAt(Instant.now());
        return transactionRepository.save(txn);
    }

    // -- The two atomic primitives every balance change goes through -----------

    /** Add money. Always safe, so this needs no condition and can never fail on balance. */
    private Wallet credit(String userId, long amountPaise) {
        return mongoTemplate.findAndModify(
                Query.query(Criteria.where("userId").is(userId)),
                new Update().inc("balancePaise", amountPaise).set("updatedAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                Wallet.class);
    }

    /**
     * Subtract money, but ONLY if there is enough. Returns the updated wallet, or null when the
     * balance was insufficient and nothing was changed.
     *
     * The whole safety of the wallet rests on this one method, so it is worth being precise
     * about why it is written this way.
     *
     * The tempting version is:
     *      wallet = repo.findByUserId(id);        // read 500
     *      if (wallet.balance >= 100) {           // check
     *          wallet.balance -= 100;             // modify in Java
     *          repo.save(wallet);                 // write 400
     *      }
     * Two requests can interleave in the gap between the read and the write. Both read 500,
     * both pass the check, both write 400 — and Rs.200 of donations came out of Rs.100. That is
     * the classic lost-update / double-spend race.
     *
     * Here, "balancePaise >= amount" is part of the QUERY and "increment by -amount" is the
     * update. MongoDB applies a matching findAndModify as one indivisible operation on the
     * document, so the two requests are forced into a strict order: the first sees 500 and
     * writes 400; the second then sees 400, fails the >= 500 condition, matches nothing, and
     * returns null. The balance therefore can never go below zero.
     */
    private Wallet debit(String userId, long amountPaise) {
        return mongoTemplate.findAndModify(
                Query.query(Criteria.where("userId").is(userId)
                        .and("balancePaise").gte(amountPaise)),
                new Update().inc("balancePaise", -amountPaise).set("updatedAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true),
                Wallet.class);
    }

    // -- Small shared helpers --------------------------------------------------

    /**
     * Rupees in, paise out. Rejects anything that is not a clean amount of money: null, zero,
     * negative, or more than two decimal places (Rs.10.005 is not a real amount).
     * longValueExact throws instead of silently rounding, which is what we want for money.
     */
    public static long toPaise(BigDecimal rupees) {
        if (rupees == null || rupees.compareTo(BigDecimal.ZERO) <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive");
        if (rupees.scale() > 2)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount cannot have more than 2 decimal places");
        try {
            return rupees.movePointRight(2).longValueExact();
        } catch (ArithmeticException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount is not a valid money value");
        }
    }

    private Account requireAccount(String email) {
        Account account = accountRepository.findByEmail(email);
        if (account == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account required");
        return account;
    }
}
