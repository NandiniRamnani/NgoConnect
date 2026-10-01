package app.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Map;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) { this.cloudinary = cloudinary; }

    @SuppressWarnings("unchecked")
    public Map<String, Object> uploadDocument(MultipartFile file, String ngoId, String docType) {
        try {
            String resourceType = isImageFile(file.getContentType()) ? "image" : "raw";
            Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "ngo-documents/" + ngoId,
                    "resource_type", resourceType,
                    "type", "private",
                    "use_filename", true,
                    "unique_filename", true,
                    "tags", docType));
            return result;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to upload document: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public String generateSignedUrl(String cloudinaryId, String resourceType) {
        try {
            long expiresAt = Instant.now().getEpochSecond() + 900;
            Map<String, Object> params = ObjectUtils.asMap("resource_type", resourceType, "type", "private", "expires_at", expiresAt);
            return cloudinary.privateDownload(cloudinaryId, "", params);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not generate document URL: " + e.getMessage());
        }
    }

    public Map<String, Object> uploadImage(MultipartFile file, String ngoId) {
        return uploadImage(file, ngoId, "ngo-media");
    }

    /** Upload a gallery photo into folder/ownerId — "ngo-media" for NGOs, "user-media" for donors. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> uploadImage(MultipartFile file, String ownerId, String folder) {
        try {
            Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", folder + "/" + ownerId, "resource_type", "image"));
            return result;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to upload image: " + e.getMessage());
        }
    }

    /**
     * Upload a video file for an NGO's gallery. resource_type "video" is required — Cloudinary
     * rejects a video sent as "image" — and it is also what deleteFile must be told later, which is
     * why NgoContentService picks the resource type from the media's type when deleting.
     */
    public Map<String, Object> uploadVideo(MultipartFile file, String ngoId) {
        return uploadVideo(file, ngoId, "ngo-media");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> uploadVideo(MultipartFile file, String ownerId, String folder) {
        try {
            return cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", folder + "/" + ownerId, "resource_type", "video"));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to upload video: " + e.getMessage());
        }
    }

    /**
     * Upload a profile picture or NGO logo, normalised to a square thumbnail.
     *
     * The transformation is the point of having a separate method rather than reusing uploadImage.
     * People upload whatever their phone produced — a 4000x3000 landscape photo, a tall portrait, a
     * 6MB PNG — and an avatar slot is a small circle. Sending the original to every visitor's
     * browser to be squashed by CSS wastes their bandwidth and still looks wrong, because CSS
     * crops from the centre and centre is rarely where the face is.
     *
     * Cloudinary does the work once, at upload time, and stores the result:
     *   width/height 400  - plenty for a circle displayed at 38px, including on high-DPI screens
     *   crop "fill"       - cover the whole square, cropping the overflow, never squash the image
     *   gravity "auto"    - let Cloudinary's content analysis pick what to keep, so a person is
     *                       centred rather than the geometric middle of the photo
     *   quality "auto"    - per-image compression, typically a fraction of the original bytes
     *   fetch_format auto - serve WebP/AVIF to browsers that accept them, JPEG to those that don't
     */
    public Map<String, Object> uploadAvatar(MultipartFile file, String ownerId, String folder) {
        try {
            return cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", folder + "/" + ownerId,
                    "resource_type", "image",
                    "transformation", new Transformation()
                            .width(400).height(400).crop("fill").gravity("auto")
                            .quality("auto").fetchFormat("auto")));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to upload image: " + e.getMessage());
        }
    }

    /**
     * Fetch a private file's bytes so the backend can serve them itself.
     *
     * Why not just hand the browser the signed URL, as generateSignedUrl does? Because Cloudinary's
     * private-download endpoint always responds with Content-Disposition: attachment, so the
     * browser saves the file instead of displaying it, and with no format requested the saved
     * file has no extension, so the operating system cannot tell what opens it. Fetching the bytes
     * here lets the controller send them back with the right Content-Type and "inline", which is
     * what makes a PDF or image open in a browser tab.
     */
    public byte[] downloadPrivate(String cloudinaryId, String resourceType) {
        String url = generateSignedUrl(cloudinaryId, resourceType);
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .connectTimeout(java.time.Duration.ofSeconds(15))
                    .build();
            java.net.http.HttpResponse<byte[]> response = client.send(
                    java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                            .timeout(java.time.Duration.ofSeconds(60)).GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Cloudinary returned HTTP " + response.statusCode() + " for this document");
            return response.body();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not fetch the document: " + e.getMessage());
        }
    }

    public void deleteFile(String cloudinaryId, String resourceType, String type) {
        try {
            cloudinary.uploader().destroy(cloudinaryId, ObjectUtils.asMap("resource_type", resourceType, "type", type));
        } catch (Exception e) {
            System.err.println("Warning: Cloudinary delete failed for " + cloudinaryId + ": " + e.getMessage());
        }
    }

    private boolean isImageFile(String contentType) { return contentType != null && contentType.startsWith("image/"); }
}
