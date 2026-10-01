package app.service;

import app.dto.DocVerificationResult.Status;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises all three read paths the service supports, so a broken native OCR
 * install (the part most likely to fail silently) shows up as a red test
 * instead of a surprise during a live demo.
 */
class DocumentVerificationServiceTest {

    private final DocumentVerificationService service = new DocumentVerificationService();

    @Test
    void nativeTextPdf_recognisedAsPanCard() throws Exception {
        byte[] pdf = textPdf("GOVERNMENT OF INDIA\nINCOME TAX DEPARTMENT\nPERMANENT ACCOUNT NUMBER\nAAATG1234C");
        var file = new MockMultipartFile("file", "pan.pdf", "application/pdf", pdf);

        var result = service.verify(file, "PAN_CARD", null, null);

        assertEquals(Status.LOOKS_VALID, result.getStatus());
    }

    @Test
    void scannedImageOnlyPdf_recoveredViaOcr() throws Exception {
        byte[] pdf = imageOnlyPdf("GOVERNMENT OF INDIA INCOME TAX DEPARTMENT PERMANENT ACCOUNT NUMBER");
        var file = new MockMultipartFile("file", "pan-scanned.pdf", "application/pdf", pdf);

        var result = service.verify(file, "PAN_CARD", null, null);

        assertEquals(Status.LOOKS_VALID, result.getStatus());
    }

    @Test
    void rawPhotoUpload_recoveredViaOcr() throws Exception {
        byte[] png = textPng("CERTIFICATE OF REGISTRATION REGISTRAR OF SOCIETIES");
        var file = new MockMultipartFile("file", "cert-photo.png", "image/png", png);

        var result = service.verify(file, "REGISTRATION_CERT", null, null);

        assertEquals(Status.LOOKS_VALID, result.getStatus());
    }

    @Test
    void wrongDocument_flaggedAsSuspicious() throws Exception {
        byte[] pdf = textPdf("RELIANCE ENERGY\nELECTRICITY BILL\nConsumer Number 1234567890\nAmount Due Rs 1200");
        var file = new MockMultipartFile("file", "electricity-bill.pdf", "application/pdf", pdf);

        var result = service.verify(file, "PAN_CARD", null, null);

        assertEquals(Status.LOOKS_SUSPICIOUS, result.getStatus());
    }

    private byte[] textPdf(String text) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setFont(PDType1Font.HELVETICA, 14);
                cs.beginText();
                cs.newLineAtOffset(60, 750);
                for (String line : text.split("\n")) {
                    cs.showText(line);
                    cs.newLineAtOffset(0, -20);
                }
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private byte[] imageOnlyPdf(String text) throws Exception {
        BufferedImage img = renderText(text);
        ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
        ImageIO.write(img, "png", pngBytes);

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDImageXObject pdImage = PDImageXObject.createFromByteArray(doc, pngBytes.toByteArray(), "scan");
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(pdImage, 50, 500, 500, 125);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private byte[] textPng(String text) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(renderText(text), "png", out);
        return out.toByteArray();
    }

    private BufferedImage renderText(String text) {
        BufferedImage img = new BufferedImage(1000, 250, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 28));
        g.drawString(text, 20, 130);
        g.dispose();
        return img;
    }
}
