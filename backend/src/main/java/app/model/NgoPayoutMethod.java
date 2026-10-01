package app.model;

import app.enums.PayoutMethodType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

/**
 * Where an NGO's withdrawals should be sent.
 *
 * WHY THIS IS ITS OWN DOCUMENT AND NOT FIELDS ON Ngo
 * -------------------------------------------------
 * The Ngo document is returned by the public NGO listing and detail pages. Putting an account
 * number on it means one careless endpoint away from publishing every NGO's bank details. A
 * separate collection makes that impossible by construction rather than by remembering.
 *
 * WHY accountNumber AND upiId ARE @JsonIgnore
 * ------------------------------------------
 * Nothing ever needs to read the full destination back — not even the NGO that typed it, who
 * gets {@code maskedAccount} instead. The one exception is the admin about to make the transfer,
 * and that goes through a single dedicated admin endpoint rather than riding along in every
 * response by default.
 */
@Document(collection = "ngo_payout_methods")
public class NgoPayoutMethod {

    @Id
    private String id;

    /** One payout destination per NGO. Replacing it overwrites this document. */
    @Indexed(unique = true)
    private String ngoId;

    private PayoutMethodType type;

    /** Must match the NGO's registered name — the admin checks this against their documents. */
    private String accountHolderName;

    @JsonIgnore private String accountNumber;
    @JsonIgnore private String upiId;

    /** Safe to show: "••••4521" for a bank account, "sh••••@okhdfcbank" for UPI. */
    private String maskedDestination;

    private String ifsc;
    private String bankName;

    /** Admin has eyeballed these details against the NGO's registration documents. */
    private boolean verified;
    private Instant verifiedAt;

    /**
     * When the details were last changed. Withdrawals are blocked for a cooling-off window
     * after this moment — see PayoutMethodService for the attack that prevents.
     */
    private Instant changedAt;

    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNgoId() { return ngoId; }
    public void setNgoId(String ngoId) { this.ngoId = ngoId; }
    public PayoutMethodType getType() { return type; }
    public void setType(PayoutMethodType type) { this.type = type; }
    public String getAccountHolderName() { return accountHolderName; }
    public void setAccountHolderName(String v) { this.accountHolderName = v; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String v) { this.accountNumber = v; }
    public String getUpiId() { return upiId; }
    public void setUpiId(String v) { this.upiId = v; }
    public String getMaskedDestination() { return maskedDestination; }
    public void setMaskedDestination(String v) { this.maskedDestination = v; }
    public String getIfsc() { return ifsc; }
    public void setIfsc(String ifsc) { this.ifsc = ifsc; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
