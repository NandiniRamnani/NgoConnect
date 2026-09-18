package app.model;

import app.enums.PayoutMethodType;
import app.enums.WithdrawalStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * An NGO asking to be paid out.
 *
 * WHY THE PAYOUT DETAILS ARE COPIED IN HERE RATHER THAN LOOKED UP
 * --------------------------------------------------------------
 * The obvious design is to store only ngoId and read NgoPayoutMethod when the admin opens the
 * request. That is wrong: the NGO can edit their payout method between requesting and being
 * paid, so the admin would transfer to a destination nobody ever approved, and afterwards there
 * would be no record of what the request actually said at the time.
 *
 * Copying the destination onto the request freezes it. What the NGO asked for, what the admin
 * approved, and where the money went are then the same three facts, permanently.
 */
@Document(collection = "withdrawal_requests")
public class WithdrawalRequest {

    @Id
    private String id;

    @Indexed
    private String ngoId;
    private String ngoName;   // denormalised so the admin queue needs no second lookup

    private long amountPaise;

    /** Indexed: the admin queue is a query on this. */
    @Indexed
    private WithdrawalStatus status;

    // ── Frozen snapshot of the payout destination at request time ──────────────
    private PayoutMethodType payoutType;
    private String payoutAccountHolderName;
    private String payoutMaskedDestination;
    private String payoutIfsc;
    private String payoutBankName;

    /** Full destination, admin-only. Never serialised — see AdminWithdrawalController. */
    @JsonIgnore private String payoutAccountNumber;
    @JsonIgnore private String payoutUpiId;

    // ── Audit trail ────────────────────────────────────────────────────────────
    private Instant requestedAt;
    private String ngoNote;

    private Instant reviewedAt;
    private String reviewedBy;    // admin username
    private String adminNote;     // rejection reason, or a note on the transfer

    private Instant paidAt;
    /** The bank/UPI reference (UTR) the admin records after transferring. */
    private String paymentReference;

    /** Rupee view for JSON only — derived, never persisted. */
    public BigDecimal getAmount() { return BigDecimal.valueOf(amountPaise, 2); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNgoId() { return ngoId; }
    public void setNgoId(String ngoId) { this.ngoId = ngoId; }
    public String getNgoName() { return ngoName; }
    public void setNgoName(String ngoName) { this.ngoName = ngoName; }
    public long getAmountPaise() { return amountPaise; }
    public void setAmountPaise(long amountPaise) { this.amountPaise = amountPaise; }
    public WithdrawalStatus getStatus() { return status; }
    public void setStatus(WithdrawalStatus status) { this.status = status; }
    public PayoutMethodType getPayoutType() { return payoutType; }
    public void setPayoutType(PayoutMethodType v) { this.payoutType = v; }
    public String getPayoutAccountHolderName() { return payoutAccountHolderName; }
    public void setPayoutAccountHolderName(String v) { this.payoutAccountHolderName = v; }
    public String getPayoutMaskedDestination() { return payoutMaskedDestination; }
    public void setPayoutMaskedDestination(String v) { this.payoutMaskedDestination = v; }
    public String getPayoutIfsc() { return payoutIfsc; }
    public void setPayoutIfsc(String v) { this.payoutIfsc = v; }
    public String getPayoutBankName() { return payoutBankName; }
    public void setPayoutBankName(String v) { this.payoutBankName = v; }
    public String getPayoutAccountNumber() { return payoutAccountNumber; }
    public void setPayoutAccountNumber(String v) { this.payoutAccountNumber = v; }
    public String getPayoutUpiId() { return payoutUpiId; }
    public void setPayoutUpiId(String v) { this.payoutUpiId = v; }
    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant v) { this.requestedAt = v; }
    public String getNgoNote() { return ngoNote; }
    public void setNgoNote(String ngoNote) { this.ngoNote = ngoNote; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant v) { this.reviewedAt = v; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String v) { this.reviewedBy = v; }
    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String adminNote) { this.adminNote = adminNote; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String v) { this.paymentReference = v; }
}
