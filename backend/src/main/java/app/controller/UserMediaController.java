package app.controller;

import app.model.UserMedia;
import app.service.UserMediaService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

/**
 * A donor's profile gallery. The writes live under /api/profile/media, which SecurityConfig already
 * restricts to logged-in accounts, and act on the caller's own profile only. Viewing someone's
 * gallery is public, like an NGO's.
 */
@RestController
public class UserMediaController {
    private final UserMediaService mediaService;

    public UserMediaController(UserMediaService mediaService) {
        this.mediaService = mediaService;
    }

    /** The logged-in user's own gallery — dashboard. */
    @GetMapping("/api/profile/media")
    public List<UserMedia> myMedia(Authentication auth) {
        return mediaService.myMedia(auth.getName());
    }

    /** Any user's gallery, for a public profile page. */
    @GetMapping("/api/users/{accountId}/media")
    public List<UserMedia> mediaForUser(@PathVariable String accountId) {
        return mediaService.mediaForAccount(accountId);
    }

    @PostMapping(value = "/api/profile/media/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserMedia uploadPhoto(@RequestPart("file") MultipartFile file,
                                 @RequestPart(value = "caption", required = false) String caption,
                                 Authentication auth) {
        return mediaService.addPhoto(auth.getName(), file, caption);
    }

    /** Save an external video link (YouTube, Google Drive, etc.). */
    @PostMapping("/api/profile/media/video")
    public UserMedia addVideoLink(@RequestBody Map<String, String> body, Authentication auth) {
        return mediaService.addVideoLink(auth.getName(), body.get("videoUrl"), body.get("caption"));
    }

    @PostMapping(value = "/api/profile/media/video/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserMedia uploadVideo(@RequestPart("file") MultipartFile file,
                                 @RequestPart(value = "caption", required = false) String caption,
                                 Authentication auth) {
        return mediaService.addVideoFile(auth.getName(), file, caption);
    }

    @DeleteMapping("/api/profile/media/{mediaId}")
    public void deleteMedia(@PathVariable String mediaId, Authentication auth) {
        mediaService.deleteMedia(auth.getName(), mediaId);
    }
}
