package app.enums;

/**
 * Life of a payout request.
 *
 * REQUESTED - NGO asked. The amount is already reserved out of their available balance,
 *             so they cannot request it twice while this one is open.
 * APPROVED  - admin checked the payout details and intends to pay. Still reserved.
 * PAID      - admin has actually transferred the money and recorded the reference.
 * REJECTED  - admin refused. The reservation is released back to available.
 * FAILED    - the transfer was attempted and bounced. Also released back to available.
 */
public enum WithdrawalStatus {
    REQUESTED, APPROVED, PAID, REJECTED, FAILED
}
