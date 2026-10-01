package app.model;

import app.enums.BeneficiaryGroup;
import app.enums.MealType;
import app.enums.VerificationStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Document(collection = "ngos")
public class Ngo {
    @Id private String id;
    private String ngoName;
    private String email;
    private String password;
    private String ngoType;
    private String legalStructure;
    private String registrationNumber;
    private String panNumber;
    private String ngoDarpanId;
    private String authorizedPersonName;
    private String authorizedPersonDesignation;
    private String description;
    private String location;
    private String address;
    private String contactPhone;
    private String uniqueNgoId;
    private VerificationStatus verificationStatus;
    private boolean active = true;
    private Instant registeredAt;
    private Instant reviewedAt;
    private String reviewNote;
    private String resetToken;
    private Instant resetTokenExpiry;

    /**
     * The organisation's logo. Same three-way meaning as {@link Account#getAvatarUrl()} — an
     * uploaded image URL, a "preset:name", or null for initials.
     *
     * This matters more for an NGO than for a person: a donor deciding where to send money is
     * looking at a list of names, and one with a real logo reads as an organisation that exists
     * while a bare letter reads as a placeholder. It is the cheapest trust signal on the page.
     */
    private String logoUrl;

    /** Cloudinary handle for the uploaded logo, so replacing it can delete the old file. */
    private String logoCloudinaryId;

    // Social / verification links (admin cross-checks these)
    private String websiteUrl;
    private String facebookUrl;
    private String instagramUrl;
    private String linkedinUrl;

    // Uploaded documents stored privately in Cloudinary
    private List<NgoDocument> documents = new ArrayList<>();

    /**
     * Food pricing the NGO sets once, instead of typing a total for every slot: what one person's
     * breakfast, lunch and dinner cost, and how many people it looks after in each group. A slot's
     * price is then costPerPerson x peopleCount, worked out on the server (see FoodSlotService),
     * and donors see the same breakdown the NGO entered. Only BREAKFAST/LUNCH/DINNER are stored;
     * a whole day is the sum of the three.
     */
    private Map<MealType, BigDecimal> mealCostPerPerson = new EnumMap<>(MealType.class);
    private Map<BeneficiaryGroup, Integer> beneficiaryCounts = new EnumMap<>(BeneficiaryGroup.class);

    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getNgoName() { return ngoName; } public void setNgoName(String v) { this.ngoName = v; }
    public String getEmail() { return email; } public void setEmail(String v) { this.email = v; }
    public String getPassword() { return password; } public void setPassword(String v) { this.password = v; }
    public String getNgoType() { return ngoType; } public void setNgoType(String v) { this.ngoType = v; }
    public String getLegalStructure() { return legalStructure; } public void setLegalStructure(String v) { this.legalStructure = v; }
    public String getRegistrationNumber() { return registrationNumber; } public void setRegistrationNumber(String v) { this.registrationNumber = v; }
    public String getPanNumber() { return panNumber; } public void setPanNumber(String v) { this.panNumber = v; }
    public String getNgoDarpanId() { return ngoDarpanId; } public void setNgoDarpanId(String v) { this.ngoDarpanId = v; }
    public String getAuthorizedPersonName() { return authorizedPersonName; } public void setAuthorizedPersonName(String v) { this.authorizedPersonName = v; }
    public String getAuthorizedPersonDesignation() { return authorizedPersonDesignation; } public void setAuthorizedPersonDesignation(String v) { this.authorizedPersonDesignation = v; }
    public String getDescription() { return description; } public void setDescription(String v) { this.description = v; }
    public String getLocation() { return location; } public void setLocation(String v) { this.location = v; }
    public String getAddress() { return address; } public void setAddress(String v) { this.address = v; }
    public String getContactPhone() { return contactPhone; } public void setContactPhone(String v) { this.contactPhone = v; }
    public String getUniqueNgoId() { return uniqueNgoId; } public void setUniqueNgoId(String v) { this.uniqueNgoId = v; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; } public void setVerificationStatus(VerificationStatus v) { this.verificationStatus = v; }
    public boolean isActive() { return active; } public void setActive(boolean v) { this.active = v; }
    public String getResetToken() { return resetToken; } public void setResetToken(String v) { this.resetToken = v; }
    public Instant getResetTokenExpiry() { return resetTokenExpiry; } public void setResetTokenExpiry(Instant v) { this.resetTokenExpiry = v; }
    public String getLogoUrl() { return logoUrl; } public void setLogoUrl(String v) { this.logoUrl = v; }
    public String getLogoCloudinaryId() { return logoCloudinaryId; } public void setLogoCloudinaryId(String v) { this.logoCloudinaryId = v; }
    public Instant getRegisteredAt() { return registeredAt; } public void setRegisteredAt(Instant v) { this.registeredAt = v; }
    public Instant getReviewedAt() { return reviewedAt; } public void setReviewedAt(Instant v) { this.reviewedAt = v; }
    public String getReviewNote() { return reviewNote; } public void setReviewNote(String v) { this.reviewNote = v; }
    public String getWebsiteUrl() { return websiteUrl; } public void setWebsiteUrl(String v) { this.websiteUrl = v; }
    public String getFacebookUrl() { return facebookUrl; } public void setFacebookUrl(String v) { this.facebookUrl = v; }
    public String getInstagramUrl() { return instagramUrl; } public void setInstagramUrl(String v) { this.instagramUrl = v; }
    public String getLinkedinUrl() { return linkedinUrl; } public void setLinkedinUrl(String v) { this.linkedinUrl = v; }
    public List<NgoDocument> getDocuments() { return documents; } public void setDocuments(List<NgoDocument> v) { this.documents = v; }
    public Map<MealType, BigDecimal> getMealCostPerPerson() { return mealCostPerPerson; } public void setMealCostPerPerson(Map<MealType, BigDecimal> v) { this.mealCostPerPerson = v; }
    public Map<BeneficiaryGroup, Integer> getBeneficiaryCounts() { return beneficiaryCounts; } public void setBeneficiaryCounts(Map<BeneficiaryGroup, Integer> v) { this.beneficiaryCounts = v; }
}
