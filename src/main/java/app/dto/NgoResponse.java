package app.dto;

import app.enums.VerificationStatus;
import app.model.Ngo;

/** Public-safe NGO response — no sensitive documents or PAN. */
public class NgoResponse {
    private final String id, ngoName, email, ngoType, description, location, address,
            contactPhone, uniqueNgoId, websiteUrl, instagramUrl, logoUrl;
    private final VerificationStatus verificationStatus;

    public NgoResponse(Ngo ngo) {
        id = ngo.getId(); ngoName = ngo.getNgoName(); email = ngo.getEmail();
        ngoType = ngo.getNgoType(); description = ngo.getDescription();
        location = ngo.getLocation(); address = ngo.getAddress(); contactPhone = ngo.getContactPhone();
        uniqueNgoId = ngo.getUniqueNgoId(); verificationStatus = ngo.getVerificationStatus();
        websiteUrl = ngo.getWebsiteUrl(); instagramUrl = ngo.getInstagramUrl();
        logoUrl = ngo.getLogoUrl();
    }

    public String getId() { return id; }
    public String getNgoName() { return ngoName; }
    public String getEmail() { return email; }
    public String getNgoType() { return ngoType; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public String getAddress() { return address; }
    public String getContactPhone() { return contactPhone; }
    public String getUniqueNgoId() { return uniqueNgoId; }
    public String getWebsiteUrl() { return websiteUrl; }
    public String getInstagramUrl() { return instagramUrl; }
    public String getLogoUrl() { return logoUrl; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
}
