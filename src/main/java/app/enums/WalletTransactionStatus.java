package app.enums;

/**
 * A top-up is written to the database BEFORE the donor pays, so it starts PENDING.
 * Only a valid Razorpay signature promotes it to COMPLETED and actually moves the balance.
 * DONATION and REFUND rows are born COMPLETED — they are internal transfers with nothing to wait for.
 */
public enum WalletTransactionStatus {
    PENDING, COMPLETED, FAILED
}
