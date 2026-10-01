package app.enums;

/**
 * Why an NGO's balance moved. Same idea as WalletTransactionType on the donor side:
 * the balance document holds the number, this explains it.
 *
 * DONATION_RECEIVED   - a donation was paid. Lands in `clearing`, not `available`.
 * CLEARED             - the settlement window passed; clearing -> available.
 * WITHDRAWAL_RESERVED - the NGO opened a request; available -> reserved.
 * WITHDRAWAL_PAID     - admin transferred the money; it leaves reserved for good.
 * WITHDRAWAL_REVERSED - the request was rejected or the transfer failed; reserved -> available.
 */
public enum NgoLedgerEntryType {
    DONATION_RECEIVED, CLEARED, WITHDRAWAL_RESERVED, WITHDRAWAL_PAID, WITHDRAWAL_REVERSED
}
