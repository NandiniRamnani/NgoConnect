package app.service;

import app.dto.AdminNgoResponse;
import app.dto.NgoRegistrationRequest;
import app.dto.NgoResponse;
import app.enums.VerificationStatus;
import app.model.Ngo;
import app.model.NgoDocument;
import app.repository.AccountRepository;
import app.repository.NgoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NgoService {
    private final NgoRepository ngoRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final CloudinaryService cloudinaryService;
    private final MailService mailService;
    private final DocumentVerificationService documentVerificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NgoService(NgoRepository ngoRepository, AccountRepository accountRepository,
                      PasswordEncoder passwordEncoder, CloudinaryService cloudinaryService,
                      MailService mailService, DocumentVerificationService documentVerificationService) {
        this.ngoRepository = ngoRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.cloudinaryService = cloudinaryService;
        this.mailService = mailService;
        this.documentVerificationService = documentVerificationService;
    }

    public NgoResponse register(String jsonData, MultipartFile regCert, MultipartFile panCard,
                                MultipartFile darpanCert, MultipartFile otherDoc) {
        NgoRegistrationRequest request;
        try {
            request = objectMapper.readValue(jsonData, NgoRegistrationRequest.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid registration data format");
        }
        validateRegistration(request);
        if (regCert == null || regCert.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration Certificate is required");
        if (panCard == null || panCard.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PAN Card document is required");

        String email = request.getEmail().trim().toLowerCase();
        String pan = request.getPanNumber().trim().toUpperCase();
        if (ngoRepository.existsByEmail(email) || accountRepository.existsByEmail(email))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        if (ngoRepository.existsByPanNumber(pan))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An application with this PAN already exists");

        Ngo ngo = new Ngo();
        ngo.setNgoName(request.getNgoName().trim());
        ngo.setEmail(email);
        ngo.setPassword(passwordEncoder.encode(request.getPassword()));
        ngo.setNgoType(request.getNgoType().trim());
        ngo.setLegalStructure(request.getLegalStructure().trim());
        ngo.setRegistrationNumber(request.getRegistrationNumber().trim());
        ngo.setPanNumber(pan);
        ngo.setNgoDarpanId(trimToNull(request.getNgoDarpanId()));
        ngo.setAuthorizedPersonName(request.getAuthorizedPersonName().trim());
        ngo.setAuthorizedPersonDesignation(request.getAuthorizedPersonDesignation().trim());
        ngo.setDescription(request.getDescription().trim());
        ngo.setLocation(request.getLocation().trim());
        ngo.setAddress(request.getAddress().trim());
        ngo.setContactPhone(normalizePhone(request.getContactPhone()));
        ngo.setWebsiteUrl(trimToNull(request.getWebsiteUrl()));
        ngo.setFacebookUrl(trimToNull(request.getFacebookUrl()));
        ngo.setInstagramUrl(trimToNull(request.getInstagramUrl()));
        ngo.setLinkedinUrl(trimToNull(request.getLinkedinUrl()));
        ngo.setUniqueNgoId("APP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        ngo.setVerificationStatus(VerificationStatus.PENDING);
        ngo.setRegisteredAt(Instant.now());

        Ngo saved = ngoRepository.save(ngo);
        String ngoId = saved.getId();

        List<NgoDocument> documents = new ArrayList<>();
        uploadDocIfPresent(regCert, ngoId, "REGISTRATION_CERT", documents, pan, request.getRegistrationNumber());
        uploadDocIfPresent(panCard, ngoId, "PAN_CARD", documents, pan, request.getRegistrationNumber());
        uploadDocIfPresent(darpanCert, ngoId, "DARPAN_CERT", documents, pan, request.getRegistrationNumber());
        uploadDocIfPresent(otherDoc, ngoId, "OTHER", documents, pan, request.getRegistrationNumber());
        saved.setDocuments(documents);
        return new NgoResponse(ngoRepository.save(saved));
    }

    /**
     * Keyword-checks the file against its declared doc type and stores the verdict on the
     * document record (for the admin review screen) — but never blocks the upload on a low
     * verdict, since a false rejection would be worse than letting a bad file through to a
     * human reviewer.
     */
    private void uploadDocIfPresent(MultipartFile file, String ngoId, String docType, List<NgoDocument> docs,
                                    String expectedPan, String expectedRegNumber) {
        if (file == null || file.isEmpty()) return;
        Map<String, Object> result = cloudinaryService.uploadDocument(file, ngoId, docType);
        NgoDocument doc = new NgoDocument();
        doc.setDocType(docType);
        doc.setFileName(file.getOriginalFilename());
        doc.setCloudinaryId((String) result.get("public_id"));
        doc.setResourceType((String) result.get("resource_type"));
        doc.setMimeType(file.getContentType());
        doc.setUploadedAt(Instant.now());
        var verification = documentVerificationService.verify(file, docType, expectedPan, expectedRegNumber);
        doc.setVerificationStatus(verification.getStatus().name());
        doc.setVerificationNote(verification.getMessage());
        docs.add(doc);
    }

    public List<NgoResponse> findApproved() {
        return ngoRepository.findByVerificationStatus(VerificationStatus.APPROVED)
                .stream().map(NgoResponse::new).toList();
    }

    public NgoResponse findApprovedById(String id) {
        Ngo ngo = ngoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NGO not found"));
        if (ngo.getVerificationStatus() != VerificationStatus.APPROVED)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "NGO not found");
        return new NgoResponse(ngo);
    }

    public List<AdminNgoResponse> findByStatus(VerificationStatus status) {
        return ngoRepository.findByVerificationStatus(status).stream().map(AdminNgoResponse::new).toList();
    }

    public AdminNgoResponse review(String id, VerificationStatus status, String reviewNote) {
        if (status != VerificationStatus.APPROVED && status != VerificationStatus.REJECTED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose APPROVED or REJECTED");
        Ngo ngo = ngoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NGO not found"));
        if (status == VerificationStatus.REJECTED && (reviewNote == null || reviewNote.isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A rejection reason is required");
        ngo.setVerificationStatus(status);
        ngo.setReviewedAt(Instant.now());
        ngo.setReviewNote(trimToNull(reviewNote));
        if (status == VerificationStatus.APPROVED && ngo.getUniqueNgoId() == null)
            ngo.setUniqueNgoId("NGO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        Ngo saved = ngoRepository.save(ngo);
        sendReviewEmail(saved);
        return new AdminNgoResponse(saved);
    }

    /**
     * Best-effort — the review itself already succeeded and is saved, so a failed
     * send here shouldn't turn into an error response for an action that worked.
     */
    private void sendReviewEmail(Ngo ngo) {
        if (ngo.getVerificationStatus() == VerificationStatus.APPROVED) {
            mailService.send(ngo.getEmail(), "Your NGO application has been approved!",
                    "Hi " + ngo.getNgoName() + ",\n\n"
                    + "Great news — your application to join NGO Connect has been approved.\n"
                    + "Your NGO ID is " + ngo.getUniqueNgoId() + ".\n\n"
                    + "You can now log in and start posting events and needs.\n\n"
                    + "— NGO Connect");
        } else if (ngo.getVerificationStatus() == VerificationStatus.REJECTED) {
            mailService.send(ngo.getEmail(), "Update on your NGO application",
                    "Hi " + ngo.getNgoName() + ",\n\n"
                    + "We've reviewed your application to join NGO Connect. It has not been approved at this time.\n\n"
                    + "Reason: " + ngo.getReviewNote() + "\n\n"
                    + "If you'd like to reapply with updated information, you're welcome to submit a new application.\n\n"
                    + "— NGO Connect");
        }
    }

    public AdminNgoResponse setActive(String id, boolean active) {
        Ngo ngo = ngoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NGO not found"));
        ngo.setActive(active);
        return new AdminNgoResponse(ngoRepository.save(ngo));
    }

    public void delete(String id) {
        if (!ngoRepository.existsById(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "NGO not found");
        ngoRepository.deleteById(id);
    }

    public String getDocumentSignedUrl(String ngoId, String cloudinaryId, String resourceType) {
        Ngo ngo = ngoRepository.findById(ngoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NGO not found"));
        boolean exists = ngo.getDocuments() != null && ngo.getDocuments().stream()
                .anyMatch(d -> cloudinaryId.equals(d.getCloudinaryId()));
        if (!exists)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        return cloudinaryService.generateSignedUrl(cloudinaryId, resourceType);
    }

    private void validateRegistration(NgoRegistrationRequest r) {
        if (blank(r.getNgoName()) || blank(r.getEmail()) || blank(r.getPassword()) || blank(r.getNgoType())
                || blank(r.getLegalStructure()) || blank(r.getRegistrationNumber()) || blank(r.getPanNumber())
                || blank(r.getAuthorizedPersonName()) || blank(r.getAuthorizedPersonDesignation())
                || blank(r.getDescription()) || blank(r.getLocation()) || blank(r.getAddress()) || blank(r.getContactPhone()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please complete all required fields");
        if (!r.getPanNumber().trim().toUpperCase().matches("[A-Z]{3}[ABCFGHLJPTK][A-Z][0-9]{4}[A-Z]"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid organisation PAN");
        validateRegistrationNumber(r.getLegalStructure().trim(), r.getRegistrationNumber().trim());
        if (!r.getEmail().trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address");
        if (r.getPassword().length() < 8)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        if (normalizePhone(r.getContactPhone()).length() < 10)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid contact phone number");
    }

    /**
     * Trust and Society registration numbers have no fixed national format — each state's
     * Registrar assigns its own scheme — so we only sanity-check length/characters for those.
     * Section 8 Companies get an MCA-issued CIN, which does have a strict 21-character format:
     * e.g. U85300MH2015NPL123456 ([L/U][5-digit industry][2-letter state][4-digit year][3-letter type][6-digit serial]).
     */
    private void validateRegistrationNumber(String legalStructure, String registrationNumber) {
        String v = registrationNumber.toUpperCase();
        if ("Section 8 Company".equalsIgnoreCase(legalStructure)) {
            if (!v.matches("[LU][0-9]{5}[A-Z]{2}[0-9]{4}[A-Z]{3}[0-9]{6}"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Enter a valid CIN, e.g. U85300MH2015NPL123456");
        } else if (!v.matches("[A-Z0-9][A-Z0-9 ./-]{3,28}[A-Z0-9]")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enter a valid registration number (5-30 characters, letters/digits/-/./space allowed)");
        }
    }

    private boolean blank(String v) { return v == null || v.isBlank(); }
    private String trimToNull(String v) { return blank(v) ? null : v.trim(); }
    private String normalizePhone(String v) { return v == null ? "" : v.replaceAll("[^0-9+]", ""); }
}
