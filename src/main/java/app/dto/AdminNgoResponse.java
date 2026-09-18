package app.dto;

import app.enums.VerificationStatus;
import app.model.Ngo;
import app.model.NgoDocument;
import java.time.Instant;
import java.util.List;

/** Full details for admin review — includes sensitive fields + documents. */
public class AdminNgoResponse {
    private final String id, ngoName, email, ngoType, legalStructure, registrationNumber,
            panNumber, ngoDarpanId, authorizedPersonName, authorizedPersonDesignation,
            description, location, address, contactPhone, uniqueNgoId, reviewNote;
    private final String websiteUrl, facebookUrl, instagramUrl, linkedinUrl;
    private final VerificationStatus verificationStatus;
    private final Instant registeredAt, reviewedAt;
    private final List<NgoDocument> documents;
    private final int documentCount;

    public AdminNgoResponse(Ngo ngo) {
        id = ngo.getId(); ngoName = ngo.getNgoName(); email = ngo.getEmail();
        ngoType = ngo.getNgoType(); legalStructure = ngo.getLegalStructure();
        registrationNumber = ngo.getRegistrationNumber(); panNumber = ngo.getPanNumber();
        ngoDarpanId = ngo.getNgoDarpanId(); authorizedPersonName = ngo.getAuthorizedPersonName();
        authorizedPersonDesignation = ngo.getAuthorizedPersonDesignation();
        description = ngo.getDescription(); location = ngo.getLocation(); address = ngo.getAddress();
        contactPhone = ngo.getContactPhone(); uniqueNgoId = ngo.getUniqueNgoId();
        verificationStatus = ngo.getVerificationStatus(); registeredAt = ngo.getRegisteredAt();
        reviewedAt = ngo.getReviewedAt(); reviewNote = ngo.getReviewNote();
        websiteUrl = ngo.getWebsiteUrl(); facebookUrl = ngo.getFacebookUrl();
        instagramUrl = ngo.getInstagramUrl(); linkedinUrl = ngo.getLinkedinUrl();
        documents = ngo.getDocuments();
        documentCount = ngo.getDocuments() != null ? ngo.getDocuments().size() : 0;
    }

    public String getId() { return id; }
    public String getNgoName() { return ngoName; }
    public String getEmail() { return email; }
    public String getNgoType() { return ngoType; }
    public String getLegalStructure() { return legalStructure; }
    public String getRegistrationNumber() { return registrationNumber; }
    public String getPanNumber() { return panNumber; }
    public String getNgoDarpanId() { return ngoDarpanId; }
    public String getAuthorizedPersonName() { return authorizedPersonName; }
    public String getAuthorizedPersonDesignation() { return authorizedPersonDesignation; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public String getAddress() { return address; }
    public String getContactPhone() { return contactPhone; }
    public String getUniqueNgoId() { return uniqueNgoId; }
    public String getReviewNote() { return reviewNote; }
    public String getWebsiteUrl() { return websiteUrl; }
    public String getFacebookUrl() { return facebookUrl; }
    public String getInstagramUrl() { return instagramUrl; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public Instant getRegisteredAt() { return registeredAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public List<NgoDocument> getDocuments() { return documents; }
    public int getDocumentCount() { return documentCount; }
}
