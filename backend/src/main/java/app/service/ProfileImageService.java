package app.service;

import app.model.Account;
import app.model.Ngo;
import app.repository.AccountRepository;
import app.repository.NgoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * Owns the one profile picture a donor or an NGO has, whichever form it takes.
 *
 * Donors and NGOs are separate collections with separate login paths, but the rules about a
 * profile picture are identical for both — the same size limit, the same file types, the same
 * "uploading a new one deletes the old one" bookkeeping, the same list of presets. Writing them
 * once here rather than twice in AccountService and NgoService is what keeps the two from drifting
 * apart, which is how you end up with a 5MB limit in one place and 10MB in the other.
 *
 * THE THREE STATES A PROFILE PICTURE CAN BE IN
 *   1. An uploaded photo   - avatarUrl is a Cloudinary https URL, and we hold its id to delete later
 *   2. A chosen preset     - avatarUrl is "preset:<name>", nothing stored remotely
 *   3. Nothing             - avatarUrl is null; the frontend draws initials
 *
 * Moving between any two of these always goes through this class, so the Cloudinary id can never
 * be left pointing at a file that is no longer shown, or cleared while the file is still there.
 */
@Service
public class ProfileImageService {

    /**
     * 5MB. Deliberately lower than the 10MB the application allows for documents: an avatar is
     * displayed in a circle a few dozen pixels wide, so anything larger is a photo the user has not
     * thought about, and Cloudinary is going to shrink it to 400x400 anyway. Rejecting it early
     * with a clear message beats spending twenty seconds of a phone's data uploading it first.
     */
    private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;

    /**
     * The illustrated avatars bundled with the frontend, for people who would rather not upload a
     * photo of themselves — which, for a site where you are visible to strangers, is a reasonable
     * thing to prefer.
     *
     * Validated against this list rather than accepting any string, because avatarUrl is rendered
     * by the browser. Without the check, "preset:" could be set to anything and the field becomes a
     * way to push attacker-chosen content into every page that displays that user.
     *
     * Names, not numbers, so the set can be reordered or extended later without silently changing
     * which avatar an existing user has.
     */
    private static final Set<String> PRESET_NAMES = Set.of(
            "leaf", "sprout", "sun", "wave", "mountain", "bloom", "star", "globe");

    private final AccountRepository accountRepository;
    private final NgoRepository ngoRepository;
    private final CloudinaryService cloudinaryService;

    public ProfileImageService(AccountRepository accountRepository, NgoRepository ngoRepository,
                               CloudinaryService cloudinaryService) {
        this.accountRepository = accountRepository;
        this.ngoRepository = ngoRepository;
        this.cloudinaryService = cloudinaryService;
    }

    // ── Donor avatars ─────────────────────────────────────────────────────────

    /** Upload and set a donor's profile photo, replacing whatever was there. */
    public String uploadUserAvatar(String email, MultipartFile file) {
        Account account = requireAccount(email);
        validate(file);

        var result = cloudinaryService.uploadAvatar(file, account.getId(), "user-avatars");
        String previousId = account.getAvatarCloudinaryId();

        account.setAvatarUrl((String) result.get("secure_url"));
        account.setAvatarCloudinaryId((String) result.get("public_id"));
        accountRepository.save(account);

        // Delete the old file only AFTER the new one is safely saved. The other order — delete
        // first, then upload — loses the user's existing picture if the upload then fails.
        deletePrevious(previousId);
        return account.getAvatarUrl();
    }

    /** Switch a donor to one of the bundled illustrations, or to nothing at all. */
    public String setUserPreset(String email, String presetName) {
        Account account = requireAccount(email);
        String previousId = account.getAvatarCloudinaryId();

        account.setAvatarUrl(normalisePreset(presetName));
        account.setAvatarCloudinaryId(null); // no longer pointing at an uploaded file
        accountRepository.save(account);

        deletePrevious(previousId);
        return account.getAvatarUrl();
    }

    // ── NGO logos ─────────────────────────────────────────────────────────────

    /** Upload and set an NGO's logo. Same rules as a donor photo; different collection. */
    public String uploadNgoLogo(String ngoId, String email, MultipartFile file) {
        Ngo ngo = requireOwnedNgo(ngoId, email);
        validate(file);

        var result = cloudinaryService.uploadAvatar(file, ngo.getId(), "ngo-logos");
        String previousId = ngo.getLogoCloudinaryId();

        ngo.setLogoUrl((String) result.get("secure_url"));
        ngo.setLogoCloudinaryId((String) result.get("public_id"));
        ngoRepository.save(ngo);

        deletePrevious(previousId);
        return ngo.getLogoUrl();
    }

    public String setNgoPreset(String ngoId, String email, String presetName) {
        Ngo ngo = requireOwnedNgo(ngoId, email);
        String previousId = ngo.getLogoCloudinaryId();

        ngo.setLogoUrl(normalisePreset(presetName));
        ngo.setLogoCloudinaryId(null);
        ngoRepository.save(ngo);

        deletePrevious(previousId);
        return ngo.getLogoUrl();
    }

    // ── Shared rules ──────────────────────────────────────────────────────────

    /**
     * Reject anything that is not a reasonable image before it reaches Cloudinary.
     *
     * The content-type check is a convenience, not a security control — a browser sets it and a
     * hand-written request can claim anything. What actually makes this safe is that Cloudinary is
     * told resource_type "image" and re-encodes what it receives: a file pretending to be a PNG
     * fails there rather than being stored and later served back to browsers.
     */
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please choose an image");

        if (file.getSize() > MAX_AVATAR_BYTES)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Image must be under 5MB. Please choose a smaller picture.");

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only image files (JPG, PNG, WebP) can be used as a profile picture");
    }

    /**
     * Turn a preset name into the value stored on the record.
     *
     * A blank name means "remove my picture", which is a legitimate choice and stores null rather
     * than an error. Anything else must be a name we ship, for the reason on PRESET_NAMES.
     */
    private String normalisePreset(String presetName) {
        if (presetName == null || presetName.isBlank()) return null;

        String name = presetName.trim().toLowerCase();
        if (!PRESET_NAMES.contains(name))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown avatar: " + presetName);

        return "preset:" + name;
    }

    /**
     * Best-effort removal of a replaced image. A failure here leaves an orphaned file in
     * Cloudinary, which costs a little storage — clearly better than failing the request and
     * telling the user their new picture did not save when it did.
     */
    private void deletePrevious(String cloudinaryId) {
        if (cloudinaryId != null) cloudinaryService.deleteFile(cloudinaryId, "image", "upload");
    }

    private Account requireAccount(String email) {
        Account account = accountRepository.findByEmail(email);
        if (account == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account required");
        return account;
    }

    /**
     * ROLE_NGO only proves the caller is *some* approved NGO. This is what proves it is THIS one —
     * without it, any logged-in NGO could replace any other organisation's logo.
     */
    private Ngo requireOwnedNgo(String ngoId, String email) {
        Ngo ngo = ngoRepository.findById(ngoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NGO not found"));
        if (!ngo.getEmail().equalsIgnoreCase(email))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage your own NGO");
        return ngo;
    }
}
