package app.dto;

import app.enums.VerificationStatus;
public class VerificationRequest {
    private VerificationStatus status;
    private String reviewNote;
    public VerificationStatus getStatus() { return status; }
    public void setStatus(VerificationStatus status) { this.status = status; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
}
