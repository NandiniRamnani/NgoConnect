package app.dto;

import java.math.BigDecimal;

/** Body of POST /api/wallet/topup/create-order — how much the user wants to add, in rupees. */
public class TopUpRequest {
    private BigDecimal amount;

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
