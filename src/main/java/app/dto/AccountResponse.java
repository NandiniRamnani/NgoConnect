package app.dto;

import app.model.Account;

public class AccountResponse {

    private final String id;
    private final String fullName;
    private final String email;
    private final String role;

    /**
     * Profile picture, sent to the frontend on login so the navbar can draw it immediately rather
     * than firing a second request just to find out what the avatar is.
     */
    private final String avatarUrl;

    public AccountResponse(Account account) {
        this.id = account.getId();
        this.fullName = account.getFullName();
        this.email = account.getEmail();
        this.role = account.getRole();
        this.avatarUrl = account.getAvatarUrl();
    }

    public AccountResponse(String id, String fullName, String email, String role) {
        this(id, fullName, email, role, null);
    }

    public AccountResponse(String id, String fullName, String email, String role, String avatarUrl) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.avatarUrl = avatarUrl;
    }

    public String getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getAvatarUrl() { return avatarUrl; }
}
