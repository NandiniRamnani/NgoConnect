package app.dto;

import java.math.BigDecimal;

/**
 * paymentMode isn't collected here — Razorpay Checkout lets the donor pick
 * card/UPI/netbanking/wallet inside its own popup, so we only need to know
 * who's donating how much to which NGO before creating the order.
 */
public class DonationRequest {
    private String ngoId;
    private BigDecimal amount;

    public String getNgoId() { return ngoId; }
    public void setNgoId(String ngoId) { this.ngoId = ngoId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
