package app.model;

import app.enums.DonationStatus;
import app.enums.PaymentMode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "donations")
public class Donation {
    @Id private String id;
    private String ngoId, userId;
    private BigDecimal amount;
    private DonationStatus status;
    private PaymentMode paymentMode;
    private String paymentReference;
    private String razorpayOrderId;

    /**
     * The 80G receipt number, e.g. "NGOC/2026-27/000042". Assigned once, at the moment the
     * donation becomes PAID, and never changed afterwards.
     *
     * Stored rather than computed on demand for a legal reason: a receipt the donor has already
     * filed with their tax return must keep saying exactly what it said the day it was issued. If
     * the number were derived at download time it could come out different after a data change,
     * and two copies of "the same" receipt would disagree. Null on donations that were never paid,
     * which is correct — there is nothing to certify.
     */
    private String receiptNumber;

    private Instant createdAt;
    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getNgoId() { return ngoId; } public void setNgoId(String ngoId) { this.ngoId = ngoId; }
    public String getUserId() { return userId; } public void setUserId(String userId) { this.userId = userId; }
    public BigDecimal getAmount() { return amount; } public void setAmount(BigDecimal amount) { this.amount = amount; }
    public DonationStatus getStatus() { return status; } public void setStatus(DonationStatus status) { this.status = status; }
    public PaymentMode getPaymentMode() { return paymentMode; } public void setPaymentMode(PaymentMode paymentMode) { this.paymentMode = paymentMode; }
    public String getPaymentReference() { return paymentReference; } public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
    public String getRazorpayOrderId() { return razorpayOrderId; } public void setRazorpayOrderId(String razorpayOrderId) { this.razorpayOrderId = razorpayOrderId; }
    public String getReceiptNumber() { return receiptNumber; } public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
