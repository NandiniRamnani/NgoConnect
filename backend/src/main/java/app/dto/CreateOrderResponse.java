package app.dto;

/** Everything the frontend needs to open the Razorpay Checkout popup. No secret ever included. */
public class CreateOrderResponse {
    private final String donationId, razorpayOrderId, currency, keyId;
    private final int amount; // in paise — the smallest INR unit, which is what Razorpay's API expects

    /**
     * True when this order is a local simulation rather than a real Razorpay order.
     *
     * The frontend needs to be told, because it decides what to open next: the real Razorpay
     * Checkout popup, or the built-in demo payment sheet. Sending the fact explicitly is better
     * than having the browser sniff the order id for a prefix — the server already knows, and one
     * side guessing at the other's conventions is how the two drift apart.
     */
    private final boolean demoMode;

    public CreateOrderResponse(String donationId, String razorpayOrderId, int amount, String currency, String keyId) {
        this(donationId, razorpayOrderId, amount, currency, keyId, false);
    }

    private CreateOrderResponse(String donationId, String razorpayOrderId, int amount,
                                String currency, String keyId, boolean demoMode) {
        this.donationId = donationId;
        this.razorpayOrderId = razorpayOrderId;
        this.amount = amount;
        this.currency = currency;
        this.keyId = keyId;
        this.demoMode = demoMode;
    }

    /**
     * A simulated order. No key id is sent because there is no Checkout popup to open and nothing
     * for the browser to authenticate to — withholding it keeps even the public half of the
     * credentials out of a response that has no use for it.
     */
    public static CreateOrderResponse demo(String donationId, String razorpayOrderId, int amount) {
        return new CreateOrderResponse(donationId, razorpayOrderId, amount, "INR", null, true);
    }

    public boolean isDemoMode() { return demoMode; }

    public String getDonationId() { return donationId; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public int getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getKeyId() { return keyId; }
}
