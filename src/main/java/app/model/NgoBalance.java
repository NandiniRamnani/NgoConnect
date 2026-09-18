package app.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * What NGOConnect owes one NGO, split across three buckets. Same paise-as-long rule as
 * {@link Wallet} — see the note there for why money is never a decimal in this codebase.
 *
 * WHY THREE BUCKETS AND NOT ONE NUMBER
 * ------------------------------------
 * A donation being marked PAID does not mean the money is in the platform's bank account yet.
 * Razorpay collects it, then settles to us on a delay (T+2 by default). Paying an NGO before
 * that settlement means paying out money we do not physically have. So the money walks through
 * three states, and only the middle one can be requested:
 *
 *   clearing   donation paid, but still inside the settlement window
 *      |       (a scheduled job moves it once the window passes)
 *   available  the NGO can request this today
 *      |       (opening a withdrawal request moves it immediately)
 *   reserved   locked inside an open request, so it cannot be requested twice
 *
 * Money leaves `reserved` for good when the admin marks the transfer done, and returns to
 * `available` if the request is rejected or the transfer bounces.
 *
 * The invariant: clearing + available + reserved + lifetimeWithdrawn == lifetimeReceived.
 */
@Document(collection = "ngo_balances")
public class NgoBalance {

    @Id
    private String id;

    /** Ngo.id of the owner. Unique — an NGO must never end up with two balance documents. */
    @Indexed(unique = true)
    private String ngoId;

    /** Received but still inside the settlement window. Not yet requestable. */
    private long clearingPaise;

    /** Settled and free. This is the only bucket a withdrawal can draw from. */
    private long availablePaise;

    /** Locked inside a REQUESTED or APPROVED withdrawal. */
    private long reservedPaise;

    /** Running totals, for the NGO's own reporting. Never decrease. */
    private long lifetimeReceivedPaise;
    private long lifetimeWithdrawnPaise;

    private Instant createdAt;
    private Instant updatedAt;

    /* Rupee views for JSON only. Spring Data maps fields, not getters, so these are never
       stored; Jackson sees them, so the UI gets printable values without doing maths. */
    public BigDecimal getClearing()         { return BigDecimal.valueOf(clearingPaise, 2); }
    public BigDecimal getAvailable()        { return BigDecimal.valueOf(availablePaise, 2); }
    public BigDecimal getReserved()         { return BigDecimal.valueOf(reservedPaise, 2); }
    public BigDecimal getLifetimeReceived() { return BigDecimal.valueOf(lifetimeReceivedPaise, 2); }
    public BigDecimal getLifetimeWithdrawn(){ return BigDecimal.valueOf(lifetimeWithdrawnPaise, 2); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNgoId() { return ngoId; }
    public void setNgoId(String ngoId) { this.ngoId = ngoId; }
    public long getClearingPaise() { return clearingPaise; }
    public void setClearingPaise(long clearingPaise) { this.clearingPaise = clearingPaise; }
    public long getAvailablePaise() { return availablePaise; }
    public void setAvailablePaise(long availablePaise) { this.availablePaise = availablePaise; }
    public long getReservedPaise() { return reservedPaise; }
    public void setReservedPaise(long reservedPaise) { this.reservedPaise = reservedPaise; }
    public long getLifetimeReceivedPaise() { return lifetimeReceivedPaise; }
    public void setLifetimeReceivedPaise(long v) { this.lifetimeReceivedPaise = v; }
    public long getLifetimeWithdrawnPaise() { return lifetimeWithdrawnPaise; }
    public void setLifetimeWithdrawnPaise(long v) { this.lifetimeWithdrawnPaise = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
