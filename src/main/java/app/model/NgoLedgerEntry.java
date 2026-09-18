package app.model;

import app.enums.NgoLedgerEntryType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One immutable line in an NGO's passbook — the counterpart of {@link WalletTransaction}
 * on the donor side. The balance document says how much; this says why.
 *
 * The DONATION_RECEIVED rows carry two extra fields the others do not need:
 * {@code clearsAt} (when the settlement window ends) and {@code cleared} (whether the
 * scheduled job has already moved this money into `available`). Those two are what let the
 * job find exactly the rows that are due, and claim each one only once.
 */
@Document(collection = "ngo_ledger_entries")
public class NgoLedgerEntry {

    @Id
    private String id;

    @Indexed
    private String ngoId;

    private NgoLedgerEntryType type;

    /** Always a positive magnitude — the direction is carried by {@code type}, never by a sign. */
    private long amountPaise;

    /** Snapshot of the available bucket right after this row was applied. */
    private Long availableAfterPaise;

    private String description;

    /** Set on DONATION_RECEIVED — which donation brought this money in. */
    private String donationId;

    /** Set on the WITHDRAWAL_* rows — which request moved it. */
    private String withdrawalId;

    /**
     * DONATION_RECEIVED only: the moment this money becomes withdrawable.
     * Indexed because the clearing job queries on it every few minutes.
     */
    @Indexed
    private Instant clearsAt;

    /** DONATION_RECEIVED only: has the clearing job already promoted this row? */
    private boolean cleared;

    private Instant createdAt;

    /** Rupee views for JSON only — derived, never persisted. */
    public BigDecimal getAmount() { return BigDecimal.valueOf(amountPaise, 2); }
    public BigDecimal getAvailableAfter() {
        return availableAfterPaise == null ? null : BigDecimal.valueOf(availableAfterPaise, 2);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNgoId() { return ngoId; }
    public void setNgoId(String ngoId) { this.ngoId = ngoId; }
    public NgoLedgerEntryType getType() { return type; }
    public void setType(NgoLedgerEntryType type) { this.type = type; }
    public long getAmountPaise() { return amountPaise; }
    public void setAmountPaise(long amountPaise) { this.amountPaise = amountPaise; }
    public Long getAvailableAfterPaise() { return availableAfterPaise; }
    public void setAvailableAfterPaise(Long v) { this.availableAfterPaise = v; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDonationId() { return donationId; }
    public void setDonationId(String donationId) { this.donationId = donationId; }
    public String getWithdrawalId() { return withdrawalId; }
    public void setWithdrawalId(String withdrawalId) { this.withdrawalId = withdrawalId; }
    public Instant getClearsAt() { return clearsAt; }
    public void setClearsAt(Instant clearsAt) { this.clearsAt = clearsAt; }
    public boolean isCleared() { return cleared; }
    public void setCleared(boolean cleared) { this.cleared = cleared; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
