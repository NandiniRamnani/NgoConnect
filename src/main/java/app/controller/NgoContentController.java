package app.controller;

import app.enums.NotificationType;
import app.model.NgoMedia;
import app.model.NgoNotification;
import app.service.NgoContentService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ngos")
public class NgoContentController {
    private final NgoContentService contentService;

    public NgoContentController(NgoContentService contentService) {
        this.contentService = contentService;
    }

    /** Get all media for a public NGO profile page. */
    @GetMapping("/{ngoId}/media")
    public List<NgoMedia> getMedia(@PathVariable String ngoId) {
        return contentService.mediaForNgo(ngoId);
    }

    /** Upload a photo to Cloudinary — NGO dashboard. */
    @PostMapping(value = "/{ngoId}/media/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public NgoMedia uploadPhoto(
            @PathVariable String ngoId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "caption", required = false) String caption,
            Authentication auth) {
        return contentService.addPhoto(ngoId, auth.getName(), file, caption);
    }

    /** Save an external video link (YouTube, Google Drive, etc.) — NGO dashboard. */
    @PostMapping("/{ngoId}/media/video")
    public NgoMedia addVideoLink(
            @PathVariable String ngoId,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        return contentService.addVideoLink(ngoId, auth.getName(), body.get("videoUrl"), body.get("caption"));
    }

    /** Delete a media item (photo or video) — NGO dashboard. */
    @DeleteMapping("/{ngoId}/media/{mediaId}")
    public void deleteMedia(
            @PathVariable String ngoId,
            @PathVariable String mediaId,
            Authentication auth) {
        contentService.deleteMedia(ngoId, mediaId, auth.getName());
    }

    /** Post a need/notification — NGO dashboard. */
    @PostMapping("/{ngoId}/notifications")
    public NgoNotification addNotification(
            @PathVariable String ngoId,
            @RequestBody NgoNotification notification,
            Authentication auth) {
        return contentService.addNotification(ngoId, auth.getName(), notification);
    }

    /** Get all active notifications/needs — public. */
    @GetMapping("/notifications")
    public List<NgoNotification> notifications(
            @RequestParam(required = false) NotificationType type) {
        return contentService.activeNotifications(type);
    }
}
