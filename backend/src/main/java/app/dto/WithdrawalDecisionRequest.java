package app.dto;

/**
 * Body for the admin actions on a withdrawal.
 * `note` carries the rejection reason or a comment; `paymentReference` is the bank/UPI
 * reference (UTR) recorded when the transfer is marked done.
 */
public class WithdrawalDecisionRequest {
    private String note;
    private String paymentReference;

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String v) { this.paymentReference = v; }
}
