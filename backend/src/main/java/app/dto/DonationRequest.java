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

    /**
     * Set when the donor is sponsoring a food slot. The NGO and the amount then come from the slot
     * record on the server, and whatever ngoId/amount the browser sent is ignored.
     */
    private String foodSlotId;

    public String getFoodSlotId() { return foodSlotId; }
    public void setFoodSlotId(String foodSlotId) { this.foodSlotId = foodSlotId; }
    public String getNgoId() { return ngoId; }
    public void setNgoId(String ngoId) { this.ngoId = ngoId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
