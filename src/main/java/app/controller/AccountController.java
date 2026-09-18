package app.controller;

import app.model.Account;
import app.service.AccountService;
import app.dto.AccountResponse;
import app.dto.LoginRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/register")
    public AccountResponse register(@RequestBody Account account) {
        return accountService.register(account);
    }

    @PostMapping("/login")
    public AccountResponse login(@RequestBody LoginRequest loginRequest) {
        return accountService.login(loginRequest.getEmail(), loginRequest.getPassword());
    }

    /** Always responds the same way, whether or not the email is registered. */
    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(@RequestBody Map<String, String> body) {
        accountService.forgotPassword(body.get("email"));
        return Map.of("message", "If that email is registered, a reset link has been sent.");
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@RequestBody Map<String, String> body) {
        accountService.resetPassword(body.get("token"), body.get("newPassword"));
        return Map.of("message", "Password updated. You can now log in.");
    }
}
