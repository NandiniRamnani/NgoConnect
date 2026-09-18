package app.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "accounts")
public class Account {

    @Id
    private String id;

    private String fullName;
    private String email;
    private String password;

    private String role;

    private boolean active = true;

    private String resetToken;
    private Instant resetTokenExpiry;

    /**
     * The user's profile picture, stored as one string that can mean three different things:
     *
     *   an https:// URL   -> a real photo they uploaded, living on Cloudinary
     *   "preset:mint-fox" -> one of the illustrated avatars bundled with the frontend
     *   null              -> no choice made; the UI draws their initials
     *
     * One nullable field instead of a separate "hasUploadedPhoto" flag plus an id plus an enum,
     * because these three are alternatives — a user has exactly one avatar, never a photo and a
     * preset at once. Storing the preset by NAME rather than by number means the set can be
     * reordered or added to later without silently changing whose face is whose.
     */
    private String avatarUrl;

    /**
     * Cloudinary's handle for an uploaded photo, kept so the old file can be deleted when the user
     * replaces it. Without this every re-upload would abandon the previous image in the account
     * forever — invisible, unreferenced, and still counting against storage.
     * Null whenever avatarUrl is a preset or empty.
     */
    private String avatarCloudinaryId;

    public Account() {
    }

    public Account(String fullName, String email, String password) {
        this.fullName = fullName;
        this.email = email;
        this.password = password;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public Instant getResetTokenExpiry() {
        return resetTokenExpiry;
    }

    public void setResetTokenExpiry(Instant resetTokenExpiry) {
        this.resetTokenExpiry = resetTokenExpiry;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getAvatarCloudinaryId() {
        return avatarCloudinaryId;
    }

    public void setAvatarCloudinaryId(String avatarCloudinaryId) {
        this.avatarCloudinaryId = avatarCloudinaryId;
    }
}
