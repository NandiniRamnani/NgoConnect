package app.service;

import app.model.Account;
import app.model.UserMedia;
import app.repository.AccountRepository;
import app.repository.UserMediaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Photos and videos a donor posts to their own profile — the user-side twin of NgoContentService's
 * media gallery, with the same limits. The owner is always resolved from the logged-in email, never
 * from a path variable, so there is no way to post to or delete from someone else's profile.
 */
@Service
public class UserMediaService {
    private static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;
    private static final String FOLDER = "user-media";

    private final AccountRepository accountRepository;
    private final UserMediaRepository mediaRepository;
    private final CloudinaryService cloudinaryService;

    public UserMediaService(AccountRepository accountRepository, UserMediaRepository mediaRepository,
                            CloudinaryService cloudinaryService) {
        this.accountRepository = accountRepository;
        this.mediaRepository = mediaRepository;
        this.cloudinaryService = cloudinaryService;
    }

    public UserMedia addPhoto(String email, MultipartFile file, String caption) {
        Account account = requireAccount(email);
        if (file == null || file.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required");
        if (file.getContentType() == null || !file.getContentType().startsWith("image/"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image files are supported");
        if (file.getSize() > MAX_PHOTO_BYTES)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photos must be 10MB or smaller");
        Map<String, Object> result = cloudinaryService.uploadImage(file, account.getId(), FOLDER);
        return save(account, (String) result.get("secure_url"), "IMAGE", caption, (String) result.get("public_id"));
    }

    public UserMedia addVideoLink(String email, String videoUrl, String caption) {
        Account account = requireAccount(email);
        if (videoUrl == null || videoUrl.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video URL is required");
        String url = videoUrl.trim();
        // The URL is rendered as a link/embed on the profile, so only allow real web addresses —
        // a "javascript:" URL here would run in the browser of whoever clicks it.
        if (!url.startsWith("https://") && !url.startsWith("http://"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video URL must start with http:// or https://");
        return save(account, url, "VIDEO", caption, null);
    }

    public UserMedia addVideoFile(String email, MultipartFile file, String caption) {
        Account account = requireAccount(email);
        if (file == null || file.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video file is required");
        if (file.getContentType() == null || !file.getContentType().startsWith("video/"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only video files are supported");
        Map<String, Object> result = cloudinaryService.uploadVideo(file, account.getId(), FOLDER);
        return save(account, (String) result.get("secure_url"), "VIDEO", caption, (String) result.get("public_id"));
    }

    public void deleteMedia(String email, String mediaId) {
        Account account = requireAccount(email);
        UserMedia media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));
        if (!media.getAccountId().equals(account.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own media");
        if (media.getCloudinaryId() != null) {
            // Cloudinary only finds the asset under the resource type it was uploaded as.
            String resourceType = "VIDEO".equals(media.getMediaType()) ? "video" : "image";
            cloudinaryService.deleteFile(media.getCloudinaryId(), resourceType, "upload");
        }
        mediaRepository.delete(media);
    }

    public List<UserMedia> myMedia(String email) {
        return mediaRepository.findByAccountIdOrderByCreatedAtDesc(requireAccount(email).getId());
    }

    public List<UserMedia> mediaForAccount(String accountId) {
        return mediaRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    private UserMedia save(Account account, String url, String type, String caption, String cloudinaryId) {
        UserMedia media = new UserMedia();
        media.setAccountId(account.getId());
        media.setMediaUrl(url);
        media.setMediaType(type);
        media.setCaption(caption != null ? caption.trim() : "");
        media.setCloudinaryId(cloudinaryId);
        media.setCreatedAt(Instant.now());
        return mediaRepository.save(media);
    }

    private Account requireAccount(String email) {
        Account account = accountRepository.findByEmail(email);
        if (account == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account required");
        return account;
    }
}
