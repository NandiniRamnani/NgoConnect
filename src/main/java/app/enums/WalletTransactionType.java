package app.enums;

/**
 * Why money moved in a wallet. Every row in the wallet ledger carries exactly one of these,
 * so a user can always answer "why is my balance this number?" by reading the history.
 *
 * TOPUP    — money came IN from outside (donor paid via Razorpay Checkout).
 * DONATION — money went OUT to an NGO (paid from the balance, no Razorpay involved).
 * REFUND   — money came BACK IN because a DONATION debit could not be completed.
 */
public enum WalletTransactionType {
    TOPUP, DONATION, REFUND
}
