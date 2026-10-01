package app.service;

import app.dto.WithdrawalCreateRequest;
import app.dto.WithdrawalDecisionRequest;
import app.enums.PayoutMethodType;
import app.enums.WithdrawalStatus;
import app.model.Ngo;
import app.model.NgoPayoutMethod;
import app.model.WithdrawalRequest;
import app.repository.NgoRepository;
import app.repository.WithdrawalRequestRepository;
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

/**
 * Drives a payout request from "the NGO asked" to "the admin transferred it", and keeps the
 * balance buckets in step at every transition.
 *
 * THE STATE MACHINE
 *
 *   REQUESTED ──approve──> APPROVED ──mark paid──> PAID
 *       │                     │
 *       └──reject──┐          └──mark failed──┐
 *                  ▼                          ▼
 *              REJECTED                    FAILED
 *          (money released back to available)
 *
 * Money is reserved the moment the request is created, not when the admin approves it. If it
 * were only reserved at approval, an NGO could open five requests for their whole balance and
 * the admin would see five approvable requests for money that exists once.
 *
 * Every transition uses a compare-and-set on {@code status} — the update only matches while the
 * request is still in the state the caller believed it was in. Two admins clicking "Approve" on
 * the same row at the same time therefore produce one approval and one clear error, instead of
 * two payouts.
 */
@Service
public class WithdrawalService {

    private final WithdrawalRequestRepository withdrawalRepository;
    private final NgoRepository ngoRepository;
    private final NgoBalanceService balanceService;
    private final PayoutMethodService payoutMethodService;
    private final MailService mailService;
    private final MongoTemplate mongoTemplate;

    /** Floor on a single payout, so a bank transfer is never worth less than its own effort. */
    @Value("${payouts.min-withdrawal-rupees:100}")
    private long minWithdrawalRupees;

    /** Where the "a new withdrawal is waiting" email goes. Blank disables the email. */
    @Value("${payouts.admin-notify-email:}")
    private String adminNotifyEmail;

    public WithdrawalService(WithdrawalRequestRepository withdrawalRepository,
                             NgoRepository ngoRepository,
                             NgoBalanceService balanceService,
                             PayoutMethodService payoutMethodService,
                             MailService mailService,
                             MongoTemplate mongoTemplate) {
        this.withdrawalRepository = withdrawalRepository;
        this.ngoRepository = ngoRepository;
        this.balanceService = balanceService;
        this.payoutMethodService = payoutMethodService;
        this.mailService = mailService;
        this.mongoTemplate = mongoTemplate;
    }

    // ── NGO side ──────────────────────────────────────────────────────────────

    public List<WithdrawalRequest> findForNgo(String ngoId) {
        return withdrawalRepository.findByNgoIdOrderByRequestedAtDesc(ngoId);
    }

    /**
     * The NGO asks to be paid.
     *
     * ORDER OF OPERATIONS
     *   1. Check the payout method exists and is outside its cooling-off window.
     *   2. Save the request as REQUESTED, with the destination FROZEN onto it.
     *   3. Reserve the money. If `available` is short this throws and the request is deleted,
     *      so no phantom request is left behind.
     *
     * The request is written before the reservation for the same reason the top-up row is
     * written before the Razorpay call: the reservation needs a real id to point at in the
     * ledger, and a request with no reservation is easy to clean up, whereas reserved money
     * with no request to explain it is not.
     */
    public WithdrawalRequest request(String ngoId, String ngoEmail, WithdrawalCreateRequest body) {
        Ngo ngo = assertNgoOwner(ngoId, ngoEmail);

        long amountPaise = WalletService.toPaise(body.getAmount());
        if (amountPaise < minWithdrawalRupees * 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Minimum withdrawal is Rs." + minWithdrawalRupees);

        NgoPayoutMethod method = payoutMethodService.requireWithdrawable(ngoId);

        WithdrawalRequest request = new WithdrawalRequest();
        request.setNgoId(ngoId);
        request.setNgoName(ngo.getNgoName());
        request.setAmountPaise(amountPaise);
        request.setStatus(WithdrawalStatus.REQUESTED);
        request.setRequestedAt(Instant.now());
        request.setNgoNote(body.getNote());
        freezeDestination(request, method);
        request = withdrawalRepository.save(request);

        if (balanceService.reserve(ngoId, amountPaise, request.getId()) == null) {
            withdrawalRepository.deleteById(request.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Not enough cleared funds available. Donations become withdrawable "
                            + "once they clear the settlement window.");
        }

        notifyAdminOfRequest(request);
        return request;
    }

    /**
     * Copies the destination onto the request so it can never drift. If the NGO edits their
     * payout method tomorrow, this request still records exactly where the money was meant to go.
     */
    private void freezeDestination(WithdrawalRequest request, NgoPayoutMethod method) {
        request.setPayoutType(method.getType());
        request.setPayoutAccountHolderName(method.getAccountHolderName());
        request.setPayoutMaskedDestination(method.getMaskedDestination());
        if (method.getType() == PayoutMethodType.BANK_ACCOUNT) {
            request.setPayoutAccountNumber(method.getAccountNumber());
            request.setPayoutIfsc(method.getIfsc());
            request.setPayoutBankName(method.getBankName());
        } else {
            request.setPayoutUpiId(method.getUpiId());
        }
    }

    // ── Admin side ────────────────────────────────────────────────────────────

    public List<WithdrawalRequest> findByStatus(WithdrawalStatus status) {
        return withdrawalRepository.findByStatusOrderByRequestedAtAsc(status);
    }

    public long countPending() {
        return withdrawalRepository.countByStatus(WithdrawalStatus.REQUESTED);
    }

    public WithdrawalRequest findById(String id) {
        return withdrawalRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Withdrawal request not found"));
    }

    /** REQUESTED -> APPROVED. Money stays reserved; nothing has been transferred yet. */
    public WithdrawalRequest approve(String id, String adminName, WithdrawalDecisionRequest body) {
        WithdrawalRequest updated = transition(id, WithdrawalStatus.REQUESTED, WithdrawalStatus.APPROVED,
                new Update()
                        .set("reviewedAt", Instant.now())
                        .set("reviewedBy", adminName)
                        .set("adminNote", body == null ? null : body.getNote()));

        // The details were good enough to approve a payout against, so record that judgement.
        payoutMethodService.markVerified(updated.getNgoId());
        return updated;
    }

    /** REQUESTED -> REJECTED. The reservation goes back to available. */
    public WithdrawalRequest reject(String id, String adminName, WithdrawalDecisionRequest body) {
        if (body == null || body.getNote() == null || body.getNote().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Give a reason so the NGO knows what to fix");

        WithdrawalRequest updated = transition(id, WithdrawalStatus.REQUESTED, WithdrawalStatus.REJECTED,
                new Update()
                        .set("reviewedAt", Instant.now())
                        .set("reviewedBy", adminName)
                        .set("adminNote", body.getNote()));

        balanceService.release(updated.getNgoId(), updated.getAmountPaise(), updated.getId(),
                "Withdrawal rejected: " + body.getNote());
        notifyNgo(updated, "Your withdrawal request was declined",
                "Reason: " + body.getNote() + "\n\nThe amount is back in your available balance.");
        return updated;
    }

    /**
     * APPROVED -> PAID. The admin has actually made the transfer and recorded its reference.
     * This is the one method that would be replaced by an API call if you ever move to
     * RazorpayX Payouts — everything around it stays the same.
     */
    public WithdrawalRequest markPaid(String id, String adminName, WithdrawalDecisionRequest body) {
        if (body == null || body.getPaymentReference() == null || body.getPaymentReference().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Record the bank or UPI reference (UTR) for this transfer");

        WithdrawalRequest updated = transition(id, WithdrawalStatus.APPROVED, WithdrawalStatus.PAID,
                new Update()
                        .set("paidAt", Instant.now())
                        .set("reviewedBy", adminName)
                        .set("paymentReference", body.getPaymentReference().trim()));

        balanceService.settle(updated.getNgoId(), updated.getAmountPaise(), updated.getId());
        notifyNgo(updated, "Your withdrawal has been paid",
                "Reference: " + updated.getPaymentReference()
                        + "\n\nIt should reach " + updated.getPayoutMaskedDestination() + " shortly.");
        return updated;
    }

    /** APPROVED -> FAILED, when the transfer bounced. The money returns to available. */
    public WithdrawalRequest markFailed(String id, String adminName, WithdrawalDecisionRequest body) {
        String reason = body == null || body.getNote() == null || body.getNote().isBlank()
                ? "Transfer failed" : body.getNote();

        WithdrawalRequest updated = transition(id, WithdrawalStatus.APPROVED, WithdrawalStatus.FAILED,
                new Update()
                        .set("reviewedAt", Instant.now())
                        .set("reviewedBy", adminName)
                        .set("adminNote", reason));

        balanceService.release(updated.getNgoId(), updated.getAmountPaise(), updated.getId(),
                "Withdrawal failed: " + reason);
        notifyNgo(updated, "Your withdrawal could not be completed",
                reason + "\n\nThe amount is back in your available balance. "
                        + "Please check your payout details and try again.");
        return updated;
    }

    // ── The one place a status ever changes ───────────────────────────────────

    /**
     * Compare-and-set on status: flip {@code from} to {@code to} only if the row is still in
     * {@code from}, and tell us whether we were the one who did it.
     *
     * A plain "load, check status, save" would let two admins approve the same request in the
     * same instant, and the money would be released or settled twice. Here the second caller
     * matches no document, gets null, and is told the request already moved on.
     */
    private WithdrawalRequest transition(String id, WithdrawalStatus from, WithdrawalStatus to, Update update) {
        WithdrawalRequest updated = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(id).and("status").is(from.name())),
                update.set("status", to.name()),
                FindAndModifyOptions.options().returnNew(true),
                WithdrawalRequest.class);

        if (updated == null) {
            WithdrawalRequest current = findById(id); // 404s if the id is simply wrong
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This request is already " + current.getStatus() + " and cannot be moved to " + to);
        }
        return updated;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Role alone is not enough: every approved NGO has ROLE_NGO. This checks it is THEIR id. */
    private Ngo assertNgoOwner(String ngoId, String ngoEmail) {
        Ngo ngo = ngoRepository.findById(ngoId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "NGO not found"));
        if (!ngo.getEmail().equalsIgnoreCase(ngoEmail))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This is not your NGO");
        return ngo;
    }

    private void notifyAdminOfRequest(WithdrawalRequest request) {
        if (adminNotifyEmail == null || adminNotifyEmail.isBlank()) return;
        mailService.send(adminNotifyEmail, "New withdrawal request: " + request.getNgoName(),
                request.getNgoName() + " has requested Rs." + request.getAmount()
                        + " to " + request.getPayoutMaskedDestination()
                        + ".\n\nReview it in the admin panel under Withdrawals.\n");
    }

    private void notifyNgo(WithdrawalRequest request, String subject, String body) {
        ngoRepository.findById(request.getNgoId()).ifPresent(ngo ->
                mailService.send(ngo.getEmail(), subject,
                        "Withdrawal of Rs." + request.getAmount() + "\n\n" + body + "\n"));
    }
}
