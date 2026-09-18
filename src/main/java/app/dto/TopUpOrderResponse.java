package app.dto;

/**
 * Everything the browser needs to open Razorpay Checkout for a wallet top-up.
 * Deliberately mirrors CreateOrderResponse (used for direct donations) so the frontend
 * can open the popup the same way in both flows — only transactionId differs from donationId.
 * The key SECRET is never in here; it stays on the server and is only used to check signatures.
 */
public class TopUpOrderResponse {
    private final String transactionId, razorpayOrderId, currency, keyId;
    private final int amount; // paise, because that is the unit Razorpay Checkout expects

    /** True when this is a local simulation — see the same field on CreateOrderResponse. */
    private final boolean demoMode;

    public TopUpOrderResponse(String transactionId, String razorpayOrderId, int amount, String currency, String keyId) {
        this(transactionId, razorpayOrderId, amount, currency, keyId, false);
    }

    private TopUpOrderResponse(String transactionId, String razorpayOrderId, int amount,
                               String currency, String keyId, boolean demoMode) {
        this.transactionId = transactionId;
        this.razorpayOrderId = razorpayOrderId;
        this.amount = amount;
        this.currency = currency;
        this.keyId = keyId;
        this.demoMode = demoMode;
    }

    public static TopUpOrderResponse demo(String transactionId, String razorpayOrderId, int amount) {
        return new TopUpOrderResponse(transactionId, razorpayOrderId, amount, "INR", null, true);
    }

    public boolean isDemoMode() { return demoMode; }

    public String getTransactionId() { return transactionId; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public int getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getKeyId() { return keyId; }
}
