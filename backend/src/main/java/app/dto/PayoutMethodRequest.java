package app.dto;

import app.enums.PayoutMethodType;

/**
 * Body of PUT /api/ngos/{ngoId}/finance/payout-method.
 * Send accountNumber + ifsc for BANK_ACCOUNT, or upiId for UPI. The service validates that
 * the fields actually match the chosen type rather than trusting whatever arrives.
 */
public class PayoutMethodRequest {
    private PayoutMethodType type;
    private String accountHolderName;
    private String accountNumber;
    private String ifsc;
    private String bankName;
    private String upiId;

    public PayoutMethodType getType() { return type; }
    public void setType(PayoutMethodType type) { this.type = type; }
    public String getAccountHolderName() { return accountHolderName; }
    public void setAccountHolderName(String v) { this.accountHolderName = v; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String v) { this.accountNumber = v; }
    public String getIfsc() { return ifsc; }
    public void setIfsc(String ifsc) { this.ifsc = ifsc; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getUpiId() { return upiId; }
    public void setUpiId(String upiId) { this.upiId = upiId; }
}
