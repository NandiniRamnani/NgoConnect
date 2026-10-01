package app.dto;

import java.math.BigDecimal;

/** Body of POST /api/ngos/{ngoId}/finance/withdrawals — how much, in rupees, plus an optional note. */
public class WithdrawalCreateRequest {
    private BigDecimal amount;
    private String note;

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
