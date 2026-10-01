package app.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

/**
 * A photo or video a donor posted to their profile. Same shape as NgoMedia, so the frontend's
 * MediaView renders either one without knowing which it has.
 */
@Document(collection = "user_media")
public class UserMedia {
    @Id private String id;
    private String accountId;
    private String mediaUrl;      // Public Cloudinary CDN URL, or an external video URL
    private String mediaType;     // "IMAGE" or "VIDEO"
    private String caption;
    private String cloudinaryId;  // Cloudinary public_id — null for video links (external URLs)
    private Instant createdAt;

    public String getId() { return id; } public void setId(String v) { this.id = v; }
    public String getAccountId() { return accountId; } public void setAccountId(String v) { this.accountId = v; }
    public String getMediaUrl() { return mediaUrl; } public void setMediaUrl(String v) { this.mediaUrl = v; }
    public String getMediaType() { return mediaType; } public void setMediaType(String v) { this.mediaType = v; }
    public String getCaption() { return caption; } public void setCaption(String v) { this.caption = v; }
    public String getCloudinaryId() { return cloudinaryId; } public void setCloudinaryId(String v) { this.cloudinaryId = v; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant v) { this.createdAt = v; }
}
