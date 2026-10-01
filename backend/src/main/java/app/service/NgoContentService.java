package app.service;

import app.model.Ngo;
import app.model.NgoMedia;
import app.model.NgoNotification;
import app.enums.NotificationType;
import app.repository.NgoMediaRepository;
import app.repository.NgoNotificationRepository;
import app.repository.NgoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class NgoContentService {
    private static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;
    private final NgoRepository ngoRepository;
    private final NgoMediaRepository mediaRepository;
    private final NgoNotificationRepository notificationRepository;
    private final CloudinaryService cloudinaryService;

    public NgoContentService(NgoRepository ngoRepository, NgoMediaRepository mediaRepository,
                             NgoNotificationRepository notificationRepository, CloudinaryService cloudinaryService) {
        this.ngoRepository = ngoRepository;
        this.mediaRepository = mediaRepository;
        this.notificationRepository = notificationRepository;
        this.cloudinaryService = cloudinaryService;
    }

    public NgoMedia addPhoto(String ngoId, String email, MultipartFile file, String caption) {
        assertOwner(ngoId, email);
        if (file == null || file.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required");
        if (file.getContentType() == null || !file.getContentType().startsWith("image/"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image files are supported");
        // The servlet limit is 100MB for videos; photos keep their original 10MB ceiling.
        if (file.getSize() > MAX_PHOTO_BYTES)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photos must be 10MB or smaller");
        Map<String, Object> result = cloudinaryService.uploadImage(file, ngoId);
        NgoMedia media = new NgoMedia();
        media.setNgoId(ngoId);
        media.setMediaUrl((String) result.get("secure_url"));
        media.setMediaType("IMAGE");
        media.setCaption(caption != null ? caption.trim() : "");
        media.setCloudinaryId((String) result.get("public_id"));
        media.setCreatedAt(Instant.now());
        return mediaRepository.save(media);
    }

    public NgoMedia addVideoLink(String ngoId, String email, String videoUrl, String caption) {
        assertOwner(ngoId, email);
        if (videoUrl == null || videoUrl.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video URL is required");
        NgoMedia media = new NgoMedia();
        media.setNgoId(ngoId);
        media.setMediaUrl(videoUrl.trim());
        media.setMediaType("VIDEO");
        media.setCaption(caption != null ? caption.trim() : "");
        media.setCloudinaryId(null);
        media.setCreatedAt(Instant.now());
        return mediaRepository.save(media);
    }

    /**
     * Upload an actual video file, as opposed to addVideoLink's YouTube/Drive URL. Stored as the
     * same VIDEO media type; a non-null cloudinaryId is what tells the frontend it can play the
     * file inline with a <video> tag rather than linking out.
     */
    public NgoMedia addVideoFile(String ngoId, String email, MultipartFile file, String caption) {
        assertOwner(ngoId, email);
        if (file == null || file.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video file is required");
        if (file.getContentType() == null || !file.getContentType().startsWith("video/"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only video files are supported");
        Map<String, Object> result = cloudinaryService.uploadVideo(file, ngoId);
        NgoMedia media = new NgoMedia();
        media.setNgoId(ngoId);
        media.setMediaUrl((String) result.get("secure_url"));
        media.setMediaType("VIDEO");
        media.setCaption(caption != null ? caption.trim() : "");
        media.setCloudinaryId((String) result.get("public_id"));
        media.setCreatedAt(Instant.now());
        return mediaRepository.save(media);
    }

    public void deleteMedia(String ngoId, String mediaId, String email) {
        assertOwner(ngoId, email);
        NgoMedia media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));
        if (!media.getNgoId().equals(ngoId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own media");
        if (media.getCloudinaryId() != null) {
            // Cloudinary only finds the asset under the resource type it was uploaded as.
            String resourceType = "VIDEO".equals(media.getMediaType()) ? "video" : "image";
            cloudinaryService.deleteFile(media.getCloudinaryId(), resourceType, "upload");
        }
        mediaRepository.delete(media);
    }

    public List<NgoMedia> mediaForNgo(String ngoId) {
        return mediaRepository.findByNgoIdOrderByCreatedAtDesc(ngoId);
    }

    public NgoNotification addNotification(String ngoId, String email, NgoNotification notification) {
        Ngo ngo = assertOwner(ngoId, email);
        if (notification.getType() == null || notification.getTitle() == null || notification.getTitle().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type and title are required");
        if (notification.getType() == NotificationType.VOLUNTEERING
                && (notification.getRequiredVolunteers() == null || notification.getRequiredVolunteers() < 1))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requiredVolunteers must be at least 1");
        if (notification.getType() == NotificationType.NEED) {
            notification.setDeliveryAddress(notification.getDeliveryAddress() == null || notification.getDeliveryAddress().isBlank()
                    ? ngo.getAddress() : notification.getDeliveryAddress().trim());
        }
        notification.setNgoId(ngoId);
        notification.setNgoName(ngo.getNgoName());
        notification.setActive(true);
        notification.setCreatedAt(Instant.now());
        return notificationRepository.save(notification);
    }

    public List<NgoNotification> activeNotifications(NotificationType type) {
        return type == null
                ? notificationRepository.findByActiveTrueOrderByUrgentDescCreatedAtDesc()
                : notificationRepository.findByActiveTrueAndTypeOrderByUrgentDescCreatedAtDesc(type);
    }

    /** Everything an NGO has posted, open and closed — for its own dashboard. */
    public List<NgoNotification> notificationsForNgo(String ngoId, String email) {
        assertOwner(ngoId, email);
        return notificationRepository.findByNgoIdOrderByCreatedAtDesc(ngoId);
    }

    /**
     * Take a need off the public list once it has been met. Matters most for urgent needs: a
     * "we need rice TODAY" post left up after it was solved sends donors to a problem that is gone.
     */
    public NgoNotification closeNotification(String ngoId, String notificationId, String email) {
        assertOwner(ngoId, email);
        NgoNotification notification = notificationRepository.findById(notificationId)
                .filter(n -> ngoId.equals(n.getNgoId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Need not found"));
        notification.setActive(false);
        return notificationRepository.save(notification);
    }

    private Ngo assertOwner(String ngoId, String email) {
        Ngo ngo = ngoRepository.findById(ngoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NGO not found"));
        if (!ngo.getEmail().equalsIgnoreCase(email))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage your own NGO");
        return ngo;
    }
}
