package app.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "ngo_media")
public class NgoMedia {
    @Id private String id;
    private String ngoId;
    private String mediaUrl;      // Public Cloudinary CDN URL (for images) or external video URL
    private String mediaType;     // "IMAGE" or "VIDEO"
    private String caption;
    private String cloudinaryId;  // Cloudinary public_id — null for video links (external URLs)
    private Instant createdAt;

    public String getId() { return id; } public void setId(String v) { this.id = v; }
    public String getNgoId() { return ngoId; } public void setNgoId(String v) { this.ngoId = v; }
    public String getMediaUrl() { return mediaUrl; } public void setMediaUrl(String v) { this.mediaUrl = v; }
    public String getMediaType() { return mediaType; } public void setMediaType(String v) { this.mediaType = v; }
    public String getCaption() { return caption; } public void setCaption(String v) { this.caption = v; }
    public String getCloudinaryId() { return cloudinaryId; } public void setCloudinaryId(String v) { this.cloudinaryId = v; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant v) { this.createdAt = v; }
}
