package app.dto;

import app.model.Account;

/** Account details for the admin panel — deliberately excludes the password hash. */
public class AdminAccountResponse {
    private final String id, fullName, email, role;
    private final boolean active;

    public AdminAccountResponse(Account account) {
        id = account.getId();
        fullName = account.getFullName();
        email = account.getEmail();
        role = account.getRole();
        active = account.isActive();
    }

    public String getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public boolean isActive() { return active; }
}
