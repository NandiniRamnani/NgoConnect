package app.enums;

/**
 * How a donation was actually funded.
 *
 * The first four are reported by Razorpay after a Checkout payment. Note that WALLET here
 * means a THIRD-PARTY wallet inside Razorpay (Paytm, PhonePe, Amazon Pay) — the money still
 * travelled through Razorpay.
 *
 * WALLET_BALANCE is different and is ours: the donor had already topped up their NGOConnect
 * balance earlier, so this particular donation never touched Razorpay at all.
 */
public enum PaymentMode {
    UPI, CARD, NET_BANKING, WALLET, WALLET_BALANCE
}
