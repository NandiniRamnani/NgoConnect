package app.dto;

public class NgoRegistrationRequest {
    private String ngoName, email, password, ngoType, legalStructure, registrationNumber, panNumber,
            ngoDarpanId, authorizedPersonName, authorizedPersonDesignation, description, location, address, contactPhone;
    // Social / verification links
    private String websiteUrl, facebookUrl, instagramUrl, linkedinUrl;

    public String getNgoName() { return ngoName; } public void setNgoName(String v) { ngoName = v; }
    public String getEmail() { return email; } public void setEmail(String v) { email = v; }
    public String getPassword() { return password; } public void setPassword(String v) { password = v; }
    public String getNgoType() { return ngoType; } public void setNgoType(String v) { ngoType = v; }
    public String getLegalStructure() { return legalStructure; } public void setLegalStructure(String v) { legalStructure = v; }
    public String getRegistrationNumber() { return registrationNumber; } public void setRegistrationNumber(String v) { registrationNumber = v; }
    public String getPanNumber() { return panNumber; } public void setPanNumber(String v) { panNumber = v; }
    public String getNgoDarpanId() { return ngoDarpanId; } public void setNgoDarpanId(String v) { ngoDarpanId = v; }
    public String getAuthorizedPersonName() { return authorizedPersonName; } public void setAuthorizedPersonName(String v) { authorizedPersonName = v; }
    public String getAuthorizedPersonDesignation() { return authorizedPersonDesignation; } public void setAuthorizedPersonDesignation(String v) { authorizedPersonDesignation = v; }
    public String getDescription() { return description; } public void setDescription(String v) { description = v; }
    public String getLocation() { return location; } public void setLocation(String v) { location = v; }
    public String getAddress() { return address; } public void setAddress(String v) { address = v; }
    public String getContactPhone() { return contactPhone; } public void setContactPhone(String v) { contactPhone = v; }
    public String getWebsiteUrl() { return websiteUrl; } public void setWebsiteUrl(String v) { websiteUrl = v; }
    public String getFacebookUrl() { return facebookUrl; } public void setFacebookUrl(String v) { facebookUrl = v; }
    public String getInstagramUrl() { return instagramUrl; } public void setInstagramUrl(String v) { instagramUrl = v; }
    public String getLinkedinUrl() { return linkedinUrl; } public void setLinkedinUrl(String v) { linkedinUrl = v; }
}
