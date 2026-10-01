package app.service;

import app.dto.PayoutDestinationResponse;
import app.dto.PayoutMethodRequest;
import app.enums.PayoutMethodType;
import app.model.NgoPayoutMethod;
import app.repository.NgoPayoutMethodRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Owns where an NGO's money gets sent. Two jobs beyond simple storage: never handing the full
 * destination back to anyone who does not need it, and making a stolen NGO login unable to
 * redirect money instantly.
 */
@Service
public class PayoutMethodService {

    /** Indian bank IFSC: four letters, a zero, then six alphanumerics. */
    private static final Pattern IFSC = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("^\\d{9,18}$");
    private static final Pattern UPI_ID = Pattern.compile("^[\\w.\\-]{2,64}@[a-zA-Z]{2,64}$");

    private final NgoPayoutMethodRepository repository;
    private final MailService mailService;

    /**
     * How long withdrawals are blocked after the payout destination changes.
     *
     * THE ATTACK THIS STOPS: someone gets hold of an NGO login, points the payout account at
     * themselves, and immediately requests a withdrawal of the whole balance. Without a delay
     * the money is gone before anyone notices. With one, the NGO gets the "your payout account
     * was changed" email while the money is still frozen, and has time to raise it.
     */
    @Value("${payouts.change-cooling-off-hours:24}")
    private long coolingOffHours;

    public PayoutMethodService(NgoPayoutMethodRepository repository, MailService mailService) {
        this.repository = repository;
        this.mailService = mailService;
    }

    /** What the NGO and admin see day to day — masked, because nobody needs the full number. */
    public Optional<NgoPayoutMethod> find(String ngoId) {
        return repository.findByNgoId(ngoId);
    }

    /**
     * Create or replace the destination. Always resets {@code verified} to false: an admin
     * approved the OLD details, and that approval says nothing about the new ones.
     */
    public NgoPayoutMethod save(String ngoId, String ngoEmail, String ngoName, PayoutMethodRequest request) {
        if (request.getType() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a bank account or UPI");
        if (isBlank(request.getAccountHolderName()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account holder name is required");

        NgoPayoutMethod method = repository.findByNgoId(ngoId).orElseGet(() -> {
            NgoPayoutMethod fresh = new NgoPayoutMethod();
            fresh.setNgoId(ngoId);
            fresh.setCreatedAt(Instant.now());
            return fresh;
        });

        method.setType(request.getType());
        method.setAccountHolderName(request.getAccountHolderName().trim());

        if (request.getType() == PayoutMethodType.BANK_ACCOUNT) {
            String account = trimmed(request.getAccountNumber());
            String ifsc = trimmed(request.getIfsc()).toUpperCase();

            if (!ACCOUNT_NUMBER.matcher(account).matches())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Account number must be 9 to 18 digits");
            if (!IFSC.matcher(ifsc).matches())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "IFSC must look like HDFC0001234");

            method.setAccountNumber(account);
            method.setIfsc(ifsc);
            method.setBankName(trimmed(request.getBankName()));
            method.setUpiId(null);
            method.setMaskedDestination(maskAccount(account));
        } else {
            String upi = trimmed(request.getUpiId());
            if (!UPI_ID.matcher(upi).matches())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "UPI ID must look like name@bank");

            method.setUpiId(upi);
            method.setAccountNumber(null);
            method.setIfsc(null);
            method.setBankName(null);
            method.setMaskedDestination(maskUpi(upi));
        }

        Instant now = Instant.now();
        method.setVerified(false);   // a new destination has never been checked by an admin
        method.setVerifiedAt(null);
        method.setChangedAt(now);    // starts the cooling-off clock
        method.setUpdatedAt(now);

        NgoPayoutMethod saved = repository.save(method);

        // Tell the NGO out-of-band. If this email surprises them, their account is compromised
        // and the cooling-off window is the time they have to do something about it.
        notifyChange(ngoEmail, ngoName, saved);
        return saved;
    }

    /** Admin has compared the details against the NGO's registration documents. */
    public NgoPayoutMethod markVerified(String ngoId) {
        NgoPayoutMethod method = requireMethod(ngoId);
        method.setVerified(true);
        method.setVerifiedAt(Instant.now());
        method.setUpdatedAt(Instant.now());
        return repository.save(method);
    }

    /**
     * Gate called before a withdrawal request is accepted. Throws with a readable reason rather
     * than returning a boolean, so the NGO is told exactly what to fix.
     */
    public NgoPayoutMethod requireWithdrawable(String ngoId) {
        NgoPayoutMethod method = repository.findByNgoId(ngoId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Add a bank account or UPI ID before requesting a withdrawal"));

        if (method.getChangedAt() != null) {
            Instant usableFrom = method.getChangedAt().plus(Duration.ofHours(coolingOffHours));
            if (Instant.now().isBefore(usableFrom))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Payout details were changed recently. Withdrawals are available from "
                                + usableFrom + " as a security measure.");
        }
        return method;
    }

    /**
     * The full destination, for the admin who is about to make the transfer. This is the ONLY
     * path in the system that returns an unmasked account number.
     */
    public PayoutDestinationResponse revealFor(String ngoId) {
        NgoPayoutMethod m = requireMethod(ngoId);
        return new PayoutDestinationResponse(m.getType(), m.getAccountHolderName(),
                m.getAccountNumber(), m.getIfsc(), m.getBankName(), m.getUpiId());
    }

    // ── Masking ───────────────────────────────────────────────────────────────

    /** 123456784521 -> "••••4521". Last four digits are enough for a human to recognise it. */
    static String maskAccount(String accountNumber) {
        String last4 = accountNumber.substring(Math.max(0, accountNumber.length() - 4));
        return "••••" + last4;
    }

    /** shiksha@okhdfcbank -> "sh••••@okhdfcbank". The handle stays; the identity does not. */
    static String maskUpi(String upiId) {
        int at = upiId.indexOf('@');
        String name = upiId.substring(0, at);
        String handle = upiId.substring(at);
        String head = name.length() <= 2 ? name : name.substring(0, 2);
        return head + "••••" + handle;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private NgoPayoutMethod requireMethod(String ngoId) {
        return repository.findByNgoId(ngoId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No payout method on file"));
    }

    /**
     * MailService returns false rather than throwing, so a mail outage cannot undo a change the
     * NGO legitimately made. The cooling-off window protects them either way.
     */
    private void notifyChange(String ngoEmail, String ngoName, NgoPayoutMethod method) {
        if (isBlank(ngoEmail)) return;
        mailService.send(ngoEmail, "Your NGOConnect payout account was changed",
                "Hello " + (ngoName == null ? "there" : ngoName) + ",\n\n"
                + "The payout destination on your NGOConnect account was just changed to "
                + method.getMaskedDestination() + ".\n\n"
                + "Withdrawals are paused for " + coolingOffHours + " hours as a security measure.\n"
                + "If you did not make this change, sign in and change your password immediately, "
                + "then contact the NGOConnect admin.\n");
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String trimmed(String s) { return s == null ? "" : s.trim(); }
}
