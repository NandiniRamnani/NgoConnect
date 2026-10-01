package app.model;

import app.enums.WalletTransactionStatus;
import app.enums.WalletTransactionType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One immutable line in the wallet's passbook. The Wallet document holds the current balance;
 * this collection holds the WHY behind it. Summing every COMPLETED row must always reproduce
 * Wallet.balancePaise — that property is what makes the wallet auditable.
 *
 * Which extra fields are filled depends on the type:
 *   TOPUP    -> razorpayOrderId + razorpayPaymentId (the outside payment that funded it)
 *   DONATION -> donationId (the Donation document this money paid for)
 *   REFUND   -> donationId (the failed donation the money is being returned from)
 */
@Document(collection = "wallet_transactions")
public class WalletTransaction {

    @Id
    private String id;

    /** Account.id of the wallet owner. Indexed because the passbook screen queries by it. */
    @Indexed
    private String userId;

    private WalletTransactionType type;
    private WalletTransactionStatus status;

    /** Always a POSITIVE magnitude. The direction of the money is carried by `type`, not by a sign. */
    private long amountPaise;

    /**
     * Balance immediately after this row was applied — a snapshot, like a bank passbook.
     * Null while a TOPUP is still PENDING, because nothing has moved yet.
     */
    private Long balanceAfterPaise;

    private String description;

    /** Set on TOPUP only. Indexed because the Razorpay verify callback looks the row up by it. */
    @Indexed
    private String razorpayOrderId;
    private String razorpayPaymentId;

    /** Set on DONATION and REFUND — links this ledger row back to the Donation document. */
    private String donationId;

    private Instant createdAt;

    /** Rupee views for the UI. Derived, so never persisted — see the same note on Wallet.getBalance(). */
    public BigDecimal getAmount() { return BigDecimal.valueOf(amountPaise, 2); }
    public BigDecimal getBalanceAfter() {
        return balanceAfterPaise == null ? null : BigDecimal.valueOf(balanceAfterPaise, 2);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public WalletTransactionType getType() { return type; }
    public void setType(WalletTransactionType type) { this.type = type; }
    public WalletTransactionStatus getStatus() { return status; }
    public void setStatus(WalletTransactionStatus status) { this.status = status; }
    public long getAmountPaise() { return amountPaise; }
    public void setAmountPaise(long amountPaise) { this.amountPaise = amountPaise; }
    public Long getBalanceAfterPaise() { return balanceAfterPaise; }
    public void setBalanceAfterPaise(Long balanceAfterPaise) { this.balanceAfterPaise = balanceAfterPaise; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public void setRazorpayOrderId(String razorpayOrderId) { this.razorpayOrderId = razorpayOrderId; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public void setRazorpayPaymentId(String razorpayPaymentId) { this.razorpayPaymentId = razorpayPaymentId; }
    public String getDonationId() { return donationId; }
    public void setDonationId(String donationId) { this.donationId = donationId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
