package app.controller;

import app.dto.DocVerificationResult;
import app.dto.NgoResponse;
import app.service.DocumentVerificationService;
import app.service.NgoService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/ngos")
public class NgoController {
    private final NgoService ngoService;
    private final DocumentVerificationService documentVerificationService;

    public NgoController(NgoService ngoService, DocumentVerificationService documentVerificationService) {
        this.ngoService = ngoService;
        this.documentVerificationService = documentVerificationService;
    }

    /**
     * Called the moment the user picks a file on the "Upload documents" step, so they find out
     * right away if it looks like the wrong document instead of only after final submission.
     * Advisory only — the frontend shows the message as a warning, it never blocks the upload.
     */
    @PostMapping(value = "/verify-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocVerificationResult verifyDocument(
            @RequestPart("file") MultipartFile file,
            @RequestParam("docType") String docType,
            @RequestParam(value = "panNumber", required = false) String panNumber,
            @RequestParam(value = "registrationNumber", required = false) String registrationNumber) {
        return documentVerificationService.verify(file, docType, panNumber, registrationNumber);
    }

    /**
     * Register a new NGO.
     * Accepts multipart/form-data:
     *   - "data" part: JSON string with all text registration fields
     *   - "regCert": Registration Certificate file (required)
     *   - "panCard": PAN Card file (required)
     *   - "darpanCert": Darpan Certificate (optional)
     *   - "otherDoc": Any other document (optional)
     */
    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public NgoResponse register(
            @RequestPart("data") String jsonData,
            @RequestPart("regCert") MultipartFile regCert,
            @RequestPart("panCard") MultipartFile panCard,
            @RequestPart(value = "darpanCert", required = false) MultipartFile darpanCert,
            @RequestPart(value = "otherDoc", required = false) MultipartFile otherDoc) {
        return ngoService.register(jsonData, regCert, panCard, darpanCert, otherDoc);
    }

    @GetMapping
    public List<NgoResponse> findApproved() {
        return ngoService.findApproved();
    }

    @GetMapping("/{id}")
    public NgoResponse findById(@PathVariable String id) {
        return ngoService.findApprovedById(id);
    }
}
