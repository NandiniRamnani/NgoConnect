package app.model;

import java.time.Instant;

/**
 * Embedded document stored inside the Ngo MongoDB document.
 * NOT a separate MongoDB collection.
 */
public class NgoDocument {
    private String docType;        // REGISTRATION_CERT | PAN_CARD | DARPAN_CERT | OTHER
    private String fileName;       // original filename shown to admin e.g. "reg_cert.pdf"
    private String cloudinaryId;   // Cloudinary public_id used to generate signed URL
    private String resourceType;   // "raw" (PDF) or "image" (JPG/PNG)
    private String mimeType;       // "application/pdf" | "image/jpeg" | "image/png"
    private Instant uploadedAt;
    private String verificationStatus; // LOOKS_VALID | LOOKS_SUSPICIOUS | UNREADABLE — result of the automatic keyword check, for admin's eyes
    private String verificationNote;   // human-readable reason behind verificationStatus

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getCloudinaryId() { return cloudinaryId; }
    public void setCloudinaryId(String cloudinaryId) { this.cloudinaryId = cloudinaryId; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }

    public String getVerificationNote() { return verificationNote; }
    public void setVerificationNote(String verificationNote) { this.verificationNote = verificationNote; }
}
