package app.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

/**
 * A conversation thread between one user and one NGO.
 */
@Document(collection = "chat_rooms")
public class ChatRoom {
    @Id private String id;
    private String ngoId;
    private String ngoEmail;
    private String ngoName;
    private String userId;
    private String userEmail;
    private String userName;
    private Instant createdAt;
    private Instant lastMessageAt;
    private String lastMessagePreview;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNgoId() { return ngoId; }
    public void setNgoId(String ngoId) { this.ngoId = ngoId; }
    public String getNgoEmail() { return ngoEmail; }
    public void setNgoEmail(String ngoEmail) { this.ngoEmail = ngoEmail; }
    public String getNgoName() { return ngoName; }
    public void setNgoName(String ngoName) { this.ngoName = ngoName; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(Instant lastMessageAt) { this.lastMessageAt = lastMessageAt; }
    public String getLastMessagePreview() { return lastMessagePreview; }
    public void setLastMessagePreview(String lastMessagePreview) { this.lastMessagePreview = lastMessagePreview; }
}
