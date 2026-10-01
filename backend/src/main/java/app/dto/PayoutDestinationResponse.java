package app.dto;

import app.enums.PayoutMethodType;

/**
 * The FULL payout destination, including the account number. Returned by exactly one
 * admin-only endpoint, at the moment the admin is about to make the transfer.
 *
 * Everywhere else in the system the destination appears masked. Having the unmasked form
 * live in its own DTO — rather than as a flag on the normal response — means a future
 * endpoint cannot leak it by accident; it has to deliberately return this class.
 */
public class PayoutDestinationResponse {
    private final PayoutMethodType type;
    private final String accountHolderName, accountNumber, ifsc, bankName, upiId;

    public PayoutDestinationResponse(PayoutMethodType type, String accountHolderName,
                                     String accountNumber, String ifsc, String bankName, String upiId) {
        this.type = type;
        this.accountHolderName = accountHolderName;
        this.accountNumber = accountNumber;
        this.ifsc = ifsc;
        this.bankName = bankName;
        this.upiId = upiId;
    }

    public PayoutMethodType getType() { return type; }
    public String getAccountHolderName() { return accountHolderName; }
    public String getAccountNumber() { return accountNumber; }
    public String getIfsc() { return ifsc; }
    public String getBankName() { return bankName; }
    public String getUpiId() { return upiId; }
}
