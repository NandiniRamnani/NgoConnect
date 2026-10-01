package app.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One wallet per user account, holding the spendable balance.
 *
 * WHY THE BALANCE IS A long OF PAISE, NOT A BigDecimal OF RUPEES
 * -------------------------------------------------------------
 * Two independent reasons:
 *  1. MongoDB's atomic $inc operator works reliably on whole numbers. We rely on $inc to add
 *     and subtract money in a single database round-trip, so that two requests arriving at the
 *     same moment can never both read "500", both subtract, and both write "400".
 *  2. Money in fractions is a classic bug source (0.1 + 0.2 != 0.3 in binary floating point).
 *     Storing the smallest indivisible unit — 1 paisa — makes every amount an exact integer.
 *
 * Razorpay's API also speaks paise, so this happens to be the same unit the payment gateway uses.
 * Rupees only reappear at the very edge, in getBalance(), for humans and for the UI.
 */
@Document(collection = "wallets")
public class Wallet {

    @Id
    private String id;

    /** Account.id of the owner. Unique — a user must never end up with two wallets. */
    @Indexed(unique = true)
    private String userId;

    /** Spendable balance in paise. 50000 means ₹500.00. Never negative. */
    private long balancePaise;

    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Rupee view of the balance, for JSON responses only.
     *
     * Spring Data MongoDB maps FIELDS, not getters, so this derived value is never written to
     * the database — but Jackson does see it, so the frontend receives a ready-to-display
     * "balance": 500.00 alongside the raw "balancePaise": 50000.
     *
     * BigDecimal.valueOf(50000, 2) builds "500.00" exactly: 50000 with the decimal point
     * shifted 2 places left. No division, so no rounding error is possible.
     */
    public BigDecimal getBalance() { return BigDecimal.valueOf(balancePaise, 2); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public long getBalancePaise() { return balancePaise; }
    public void setBalancePaise(long balancePaise) { this.balancePaise = balancePaise; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
