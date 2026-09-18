package app.dto;

/**
 * Outcome of the keyword check we run on an uploaded document.
 * This is advisory only (we warn, we never block the user), so it always
 * carries a human-readable reason the frontend can show directly.
 */
public class DocVerificationResult {

    public enum Status {
        /** Text was readable and contained the words we expect for this doc type. */
        LOOKS_VALID,
        /** Text was readable but didn't contain the words we expect. Likely wrong file. */
        LOOKS_SUSPICIOUS,
        /** We couldn't extract any text (e.g. a scanned photo saved as PDF, or an image file). */
        UNREADABLE
    }

    private final Status status;
    private final String message;

    public DocVerificationResult(Status status, String message) {
        this.status = status;
        this.message = message;
    }

    public Status getStatus() { return status; }
    public String getMessage() { return message; }
    public boolean isVerified() { return status == Status.LOOKS_VALID; }
}
