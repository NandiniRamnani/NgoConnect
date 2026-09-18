package app.controller;

import app.dto.AdminNgoResponse;
import app.dto.VerificationRequest;
import app.enums.VerificationStatus;
import app.service.NgoService;
import org.springframework.web.bind.annotation.*;
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
