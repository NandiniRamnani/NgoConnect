package app.controller;

import app.dto.AdminNgoResponse;
import app.dto.VerificationRequest;
import app.enums.VerificationStatus;
import app.model.NgoDocument;
import app.service.NgoService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ngos")
public class AdminController {
    private final NgoService ngoService;

    public AdminController(NgoService ngoService) {
        this.ngoService = ngoService;
    }

    /** List NGO applications by status: PENDING, APPROVED, or REJECTED. */
    @GetMapping
    public List<AdminNgoResponse> findByStatus(
            @RequestParam(defaultValue = "PENDING") VerificationStatus status) {
        return ngoService.findByStatus(status);
    }

    /** Approve or reject an NGO application. */
    @PatchMapping("/{id}/verification")
    public AdminNgoResponse review(
            @PathVariable String id,
            @RequestBody VerificationRequest request) {
        return ngoService.review(id, request.getStatus(), request.getReviewNote());
    }

    /**
     * Get a 15-minute signed Cloudinary URL to view a private NGO document.
     * Admin only — secured by Spring Security.
     */
    @GetMapping("/{ngoId}/document-url")
    public Map<String, String> getDocumentUrl(
            @PathVariable String ngoId,
            @RequestParam String cloudinaryId,
            @RequestParam String resourceType) {
        String url = ngoService.getDocumentSignedUrl(ngoId, cloudinaryId, resourceType);
        return Map.of("url", url);
    }

    /**
     * Stream an NGO document back with its real type, for viewing in a browser tab.
     *
     * The two headers are the whole fix. Content-Type tells the browser this is a PDF or an image
     * rather than unknown bytes, so it renders it with its built-in viewer; Content-Disposition
     * "inline" says to display it rather than save it, and still carries the original filename
     * (with its extension) so that a manual "Save as" produces a file the OS can open.
     */
    @GetMapping("/{ngoId}/document")
    public ResponseEntity<byte[]> viewDocument(@PathVariable String ngoId,
                                               @RequestParam String cloudinaryId) {
        NgoDocument document = ngoService.findDocument(ngoId, cloudinaryId);
        byte[] bytes = ngoService.documentBytes(document);

        String fileName = document.getFileName() == null ? "document" : document.getFileName();
        MediaType type = resolveType(document.getMimeType(), fileName, document.getResourceType());
        fileName = ensureExtension(fileName, type);

        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }

    /**
     * Stored mimeType first; if an older record lacks it, infer from the filename, then from the
     * Cloudinary resource type. Only as a last resort fall back to generic bytes.
     */
    private static MediaType resolveType(String mimeType, String fileName, String resourceType) {
        if (mimeType != null && !mimeType.isBlank()) {
            try { return MediaType.parseMediaType(mimeType); } catch (Exception ignored) { }
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if ("raw".equals(resourceType)) return MediaType.APPLICATION_PDF; // documents upload as raw only when they are PDFs
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /** A filename without an extension is exactly the "file that can't be opened" problem. */
    private static String ensureExtension(String fileName, MediaType type) {
        if (fileName.contains(".")) return fileName;
        if (MediaType.APPLICATION_PDF.includes(type)) return fileName + ".pdf";
        if (MediaType.IMAGE_PNG.includes(type)) return fileName + ".png";
        if (MediaType.IMAGE_JPEG.includes(type)) return fileName + ".jpg";
        return fileName;
    }

    /** Suspend or reinstate an already-approved NGO, independent of the application review status. */
    @PatchMapping("/{id}/active")
    public AdminNgoResponse setActive(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        return ngoService.setActive(id, Boolean.TRUE.equals(body.get("active")));
    }

    /** Permanently remove an NGO and its application. */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        ngoService.delete(id);
    }
}
