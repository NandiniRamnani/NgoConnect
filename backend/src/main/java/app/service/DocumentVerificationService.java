package app.service;

import app.dto.DocVerificationResult;
import app.dto.DocVerificationResult.Status;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Sanity check for uploaded NGO documents — no paid AI involved. Two layers:
 *
 * 1. If the PDF has a real text layer (the common case for anything generated
 *    digitally — an e-Darpan certificate, an MCA-issued CIN certificate, etc.)
 *    we read it directly with PDFBox and keyword-match it, same as before.
 *
 * 2. If there's no text layer — a scanned certificate, or a photo of a PAN
 *    card uploaded as JPG/PNG — we fall back to Tesseract OCR (open-source,
 *    runs locally, no API key, no per-scan cost) to read the pixels, then run
 *    the same keyword match against whatever OCR could recover.
 *
 * Verdicts are always advisory: callers warn the user, they never block the
 * upload, because OCR misreads are common enough that a hard rejection would
 * do more harm than good — a human admin still reviews every application.
 */
@Service
public class DocumentVerificationService {

    private static final int MIN_KEYWORD_HITS = 1;
    private static final int MAX_OCR_PAGES = 2; // registration certs/PAN cards are almost always 1 page — keep OCR fast

    private static final Map<String, List<String>> KEYWORDS_BY_DOC_TYPE = Map.of(
        "REGISTRATION_CERT", List.of(
            "certificate of registration", "registration certificate", "certificate of incorporation",
            "registrar of societies", "registrar of firms", "registrar of companies",
            "societies registration act", "indian trusts act", "trust deed",
            "ministry of corporate affairs", "section 8", "incorporation", "registered under"
        ),
        "PAN_CARD", List.of(
            "income tax department", "permanent account number", "govt. of india",
            "government of india", "income tax pan services unit", "income tax act"
        ),
        "DARPAN_CERT", List.of(
            "darpan", "niti aayog", "unique id"
        )
    );

    private static final Pattern PAN_PATTERN = Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]");

    private final Tesseract tesseract = new Tesseract();
    private final boolean ocrReady;

    public DocumentVerificationService() {
        boolean ready;
        try {
            tesseract.setDatapath(extractTessData());
            tesseract.setLanguage("eng");
            ready = true;
        } catch (IOException e) {
            // Trained-data file missing/corrupt — OCR step is skipped, native-text PDFs still work fine.
            System.err.println("OCR unavailable, falling back to text-only document checks: " + e.getMessage());
            ready = false;
        }
        this.ocrReady = ready;
    }

    /**
     * @param docType           one of REGISTRATION_CERT | PAN_CARD | DARPAN_CERT | OTHER
     * @param expectedPan       the org PAN entered in the form, used as a bonus cross-check
     *                          for PAN_CARD uploads (ignored for other doc types)
     * @param expectedRegNumber the registration number entered in the form, used as a bonus
     *                          cross-check for REGISTRATION_CERT uploads
     */
    public DocVerificationResult verify(MultipartFile file, String docType, String expectedPan, String expectedRegNumber) {
        List<String> keywords = KEYWORDS_BY_DOC_TYPE.get(docType);
        if (keywords == null) {
            return new DocVerificationResult(Status.UNREADABLE, "No automatic check available for this document type.");
        }

        String contentType = file.getContentType();
        boolean isPdf = "application/pdf".equals(contentType);
        boolean isImage = contentType != null && contentType.startsWith("image/");
        if (!isPdf && !isImage) {
            return new DocVerificationResult(Status.UNREADABLE, "Unsupported file type for automatic checking.");
        }

        try {
            if (isPdf) {
                String text = extractText(file);
                if (text != null && !text.isBlank()) {
                    return classify(text, docType, keywords, expectedPan, expectedRegNumber, false);
                }
                return ocrFallback(() -> ocrPdf(file), docType, keywords, expectedPan, expectedRegNumber,
                    "This looks like a scanned copy and we couldn't read any text from it, even with OCR. Please double check it's the right file.");
            }
            return ocrFallback(() -> ocrImage(file), docType, keywords, expectedPan, expectedRegNumber,
                "We couldn't read any text from this image. Please make sure it's a clear, well-lit photo of the document.");
        } catch (IOException | TesseractException e) {
            return new DocVerificationResult(Status.UNREADABLE, "Couldn't read this file automatically — it may be corrupted or unsupported.");
        }
    }

    private interface OcrCall { String run() throws IOException, TesseractException; }

    private DocVerificationResult ocrFallback(OcrCall ocr, String docType, List<String> keywords,
                                              String expectedPan, String expectedRegNumber, String unreadableMessage)
            throws IOException, TesseractException {
        if (!ocrReady) {
            return new DocVerificationResult(Status.UNREADABLE,
                "This document has no readable text layer, and OCR isn't available on this server right now — please double check it's the right file.");
        }
        String ocrText = ocr.run();
        if (ocrText == null || ocrText.isBlank()) {
            return new DocVerificationResult(Status.UNREADABLE, unreadableMessage);
        }
        return classify(ocrText, docType, keywords, expectedPan, expectedRegNumber, true);
    }

    private DocVerificationResult classify(String text, String docType, List<String> keywords,
                                           String expectedPan, String expectedRegNumber, boolean viaOcr) {
        String normalized = text.toLowerCase(Locale.ROOT);
        String suffix = viaOcr ? " (read via OCR from a scanned copy — double check it manually too)" : "";

        if ("PAN_CARD".equals(docType) && expectedPan != null && !expectedPan.isBlank()
                && normalized.contains(expectedPan.toLowerCase(Locale.ROOT))) {
            return new DocVerificationResult(Status.LOOKS_VALID, "PAN number on the document matches the PAN you entered." + suffix);
        }
        if ("REGISTRATION_CERT".equals(docType) && expectedRegNumber != null && !expectedRegNumber.isBlank()
                && normalized.contains(expectedRegNumber.toLowerCase(Locale.ROOT))) {
            return new DocVerificationResult(Status.LOOKS_VALID, "Registration number on the document matches the number you entered." + suffix);
        }

        long hits = keywords.stream().filter(normalized::contains).count();
        if (hits >= MIN_KEYWORD_HITS) {
            return new DocVerificationResult(Status.LOOKS_VALID, "This looks like a valid " + label(docType) + "." + suffix);
        }

        if ("PAN_CARD".equals(docType) && PAN_PATTERN.matcher(text.toUpperCase(Locale.ROOT)).find()) {
            return new DocVerificationResult(Status.LOOKS_VALID, "Found a PAN-formatted number on this document." + suffix);
        }

        return new DocVerificationResult(Status.LOOKS_SUSPICIOUS,
            "This file doesn't look like a " + label(docType) + " — please double check you've uploaded the right document." + suffix);
    }

    private String extractText(MultipartFile file) throws IOException {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String ocrPdf(MultipartFile file) throws IOException, TesseractException {
        StringBuilder sb = new StringBuilder();
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(document.getNumberOfPages(), MAX_OCR_PAGES);
            for (int i = 0; i < pages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 300, ImageType.GRAY);
                sb.append(tesseract.doOCR(image)).append('\n');
            }
        }
        return sb.toString();
    }

    private String ocrImage(MultipartFile file) throws IOException, TesseractException {
        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null) return null;
        return tesseract.doOCR(image);
    }

    /** Tesseract needs a real filesystem folder to find eng.traineddata — copy it out of the jar once, at startup. */
    private String extractTessData() throws IOException {
        Path targetDir = Files.createTempDirectory("ngoconnect-tessdata");
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("tessdata/eng.traineddata")) {
            if (in == null) throw new IOException("tessdata/eng.traineddata not found on classpath");
            Files.copy(in, targetDir.resolve("eng.traineddata"));
        }
        return targetDir.toString();
    }

    private String label(String docType) {
        return switch (docType) {
            case "REGISTRATION_CERT" -> "Registration Certificate";
            case "PAN_CARD" -> "PAN Card";
            case "DARPAN_CERT" -> "NGO Darpan Certificate";
            default -> "document";
        };
    }
}
