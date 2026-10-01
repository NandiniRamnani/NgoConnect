package app.service;

import app.model.Account;
import app.repository.AccountRepository;
import app.repository.NgoRepository;
import app.dto.AccountResponse;
import app.dto.AdminAccountResponse;
import app.enums.VerificationStatus;
import app.model.Ngo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final NgoRepository ngoRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final String frontendUrl;

    /**
     * How long a password-reset link stays usable, in minutes.
     *
     * Short on purpose. The link is a temporary key to the account, and it sits in an inbox where
     * it can be read by anyone who later gains access to that mailbox, a forwarded thread, or an
     * unlocked phone showing a notification preview. Every extra minute is time in which a link
     * that has already served its purpose can still be used by someone else.
     *
     * Fifteen minutes is comfortably longer than the few seconds a person actually needs to open
     * the mail and choose a new password, while being far too short to be worth stealing later.
     * A user who takes longer simply requests another link, which costs them one click.
     */
    private final long resetTokenMinutes;

    /**
     * The address the application signs into to send mail. Read here only to answer one question:
     * has anybody actually configured mail on this deployment?
     *
     * The placeholder check matters as much as the blank check. A fresh copy of .env.example
     * carries "youraddress@gmail.com", which is not a real account, so an unedited file fails at
     * the SMTP login with an error that says nothing about the real cause. Treating the untouched
     * placeholder as "not configured" turns that dead end into the console fallback below.
     */
    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    /**
     * Mail counts as configured only when BOTH the address and the password are real.
     *
     * Checking the address alone is not enough, and the reason is worth stating. The moment a real
     * address is filled in, this method would start returning true, the console fallback would
     * switch off, and the application would begin attempting real SMTP logins with whatever
     * placeholder still sits in the password field — failing every reset with an error, which is
     * exactly the dead end the fallback exists to prevent. Requiring both means the feature keeps
     * working through a half-finished setup, and switches to real email only once it can succeed.
     *
     * A Gmail App Password is always exactly sixteen characters, so anything of a different length
     * is either a placeholder or an ordinary account password, neither of which Google accepts.
     */
    private static final String PLACEHOLDER_USER = "youraddress@gmail.com";
    private static final int APP_PASSWORD_LENGTH = 16;

    private boolean mailConfigured() {
        if (mailUsername == null || mailUsername.isBlank()) return false;
        if (mailUsername.trim().equalsIgnoreCase(PLACEHOLDER_USER)) return false;
        if (mailPassword == null) return false;
        return mailPassword.trim().length() == APP_PASSWORD_LENGTH;
    }

    public AccountService(AccountRepository accountRepository, NgoRepository ngoRepository,
                      PasswordEncoder passwordEncoder, MailService mailService,
                      @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
                      @Value("${security.reset-token-minutes:15}") long resetTokenMinutes) {
    this.accountRepository = accountRepository;
    this.ngoRepository = ngoRepository;
    this.passwordEncoder = passwordEncoder;
    this.mailService = mailService;
    this.frontendUrl = frontendUrl;
    this.resetTokenMinutes = resetTokenMinutes;
}
    public AccountResponse register(Account account) {
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration data is required");
        }

        String email = account.getEmail() == null ? "" : account.getEmail().trim().toLowerCase();
        String password = account.getPassword();
        String fullName = account.getFullName() == null ? "" : account.getFullName().trim();

        if (fullName.isBlank() || email.isBlank() || password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please complete all required fields");
        }
        if (password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }
        if (accountRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        account.setFullName(fullName);
        account.setEmail(email);
        account.setRole("USER");
        account.setPassword(passwordEncoder.encode(password));
        return new AccountResponse(accountRepository.save(account));
    }

    public AccountResponse login(String email, String password) {
        Account account = accountRepository.findByEmail(email);

        if (account != null && passwordEncoder.matches(password, account.getPassword())) {
            if (!account.isActive())
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account has been deactivated");
            return new AccountResponse(account);
        }

        Ngo ngo = ngoRepository.findByEmail(email);
        if (ngo != null && passwordEncoder.matches(password, ngo.getPassword())) {
            if (ngo.getVerificationStatus() != VerificationStatus.APPROVED) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NGO registration is " + ngo.getVerificationStatus().name().toLowerCase());
            }
            if (!ngo.isActive())
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This NGO account has been deactivated");
            return new AccountResponse(ngo.getId(), ngo.getNgoName(), ngo.getEmail(), "NGO", ngo.getLogoUrl());
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    /**
     * Always returns normally, whether or not the email is registered — so this
     * endpoint can't be used to check which emails have accounts.
     */
    public void forgotPassword(String email) {
        if (email == null || email.isBlank()) return;
        email = email.trim().toLowerCase();
        String token = UUID.randomUUID().toString();
        Instant expiry = Instant.now().plus(Duration.ofMinutes(resetTokenMinutes));

        Account account = accountRepository.findByEmail(email);
        if (account != null) {
            account.setResetToken(token);
            account.setResetTokenExpiry(expiry);
            accountRepository.save(account);
            sendResetEmail(email, account.getFullName(), token);
            return;
        }

        Ngo ngo = ngoRepository.findByEmail(email);
        if (ngo != null) {
            ngo.setResetToken(token);
            ngo.setResetTokenExpiry(expiry);
            ngoRepository.save(ngo);
            sendResetEmail(email, ngo.getNgoName(), token);
        }
    }

    private void sendResetEmail(String to, String name, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;

        /*
         * NO MAIL CONFIGURED: print the link to the server console instead of failing.
         *
         * Sending real email requires a Gmail App Password, which in turn requires 2-Step
         * Verification on a Google account — a setup step that has nothing to do with this
         * codebase and that a developer or a demonstrator may reasonably not want to perform.
         * Without this branch the whole feature is unusable until that is done, and the user is
         * shown an error that makes it look as though the reset logic itself is broken.
         *
         * Everything that actually constitutes the feature still runs: the token was generated
         * and saved with its expiry before this method was called, the link below is genuine, and
         * pasting it into the browser exercises the identical single-use, time-limited path a
         * mailed link would. Only the delivery method differs.
         *
         * This is reached ONLY when mail is unconfigured. A deployment that HAS configured mail
         * and then fails to send still raises an error further down, because there the failure is
         * a real fault that needs to be seen rather than worked around.
         */
        if (!mailConfigured()) {
            printResetLinkToConsole(to, link);
            return;
        }

        boolean sent = mailService.send(to, "Reset your NGO Connect password",
                "Hi " + name + ",\n\n"
                + "We received a request to reset your password. Click the link below to choose a new one:\n\n"
                + link + "\n\n"
                // Read from the same field the expiry is calculated from, so the email can never
                // promise a window that differs from the one actually enforced.
                + "This link expires in " + resetTokenMinutes + " minutes and can only be used once.\n"
                + "If you didn't request this, you can safely ignore this email — your password\n"
                + "will not change unless the link above is opened.\n\n"
                + "— NGO Connect");
        if (!sent)
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not send the reset email. Please try again later.");
    }

    /**
     * Prints the reset link where the person running the server will actually see it.
     *
     * Framed by a loud banner rather than written through the logger, because it has to be found
     * instantly in a console that is already scrolling with Spring's own startup and request
     * output. It also states plainly that mail is not configured, so this never reads as normal
     * behaviour that someone might ship to production by accident.
     */
    private void printResetLinkToConsole(String to, String link) {
        System.out.println();
        System.out.println("  ################################################################");
        System.out.println("  #  PASSWORD RESET LINK  (email is not configured on this server) #");
        System.out.println("  ################################################################");
        System.out.println("  For : " + to);
        System.out.println("  Link: " + link);
        System.out.println("  Valid for " + resetTokenMinutes + " minutes, and usable only once.");
        System.out.println("  Copy the link above into your browser to reset the password.");
        System.out.println("  To send real email instead, set MAIL_USERNAME and MAIL_PASSWORD in .env");
        System.out.println("  ################################################################");
        System.out.println();
    }

    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset link is invalid");
        if (newPassword == null || newPassword.length() < 8)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");

        Account account = accountRepository.findByResetToken(token);
        if (account != null) {
            requireNotExpired(account.getResetTokenExpiry());
            account.setPassword(passwordEncoder.encode(newPassword));
            account.setResetToken(null);
            account.setResetTokenExpiry(null);
            accountRepository.save(account);
            return;
        }

        Ngo ngo = ngoRepository.findByResetToken(token);
        if (ngo != null) {
            requireNotExpired(ngo.getResetTokenExpiry());
            ngo.setPassword(passwordEncoder.encode(newPassword));
            ngo.setResetToken(null);
            ngo.setResetTokenExpiry(null);
            ngoRepository.save(ngo);
            return;
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This reset link is invalid or has already been used");
    }

    private void requireNotExpired(Instant expiry) {
        if (expiry == null || expiry.isBefore(Instant.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This reset link has expired. Please request a new one.");
    }

    // ── Admin management ────────────────────────────────────────────────────
    public List<AdminAccountResponse> findAllForAdmin() {
        return accountRepository.findAll().stream().map(AdminAccountResponse::new).toList();
    }

    public AdminAccountResponse setActive(String id, boolean active) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        account.setActive(active);
        return new AdminAccountResponse(accountRepository.save(account));
    }

    public void delete(String id) {
        if (!accountRepository.existsById(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        accountRepository.deleteById(id);
    }
}
