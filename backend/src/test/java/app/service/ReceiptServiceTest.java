package app.service;

import app.enums.DonationStatus;
import app.enums.PaymentMode;
import app.model.Donation;
import app.model.Ngo;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A tax receipt is read by a human and sometimes by the tax office, so the things worth testing are
 * the ones a compiler cannot catch: that the PDF is actually a valid, readable PDF rather than
 * bytes that happen not to throw, that every legally required field made it onto the page, and that
 * the amount in words says what the figure says.
 *
 * The words converter gets the most attention because it is the only real algorithm here, and
 * because Indian place values (lakh, crore) group differently from the English the JDK could have
 * helped with.
 */
class ReceiptServiceTest {

    // Only nextReceiptNumber() touches Mongo; generatePdf and the words converter are pure, so a
    // dead mock is enough to construct the service for those.
    private final ReceiptService service = new ReceiptService(mock(org.springframework.data.mongodb.core.MongoTemplate.class));

    private static Donation paidDonation(String amount) {
        Donation d = new Donation();
        d.setId("don1");
        d.setNgoId("ngo1");
        d.setUserId("user1");
        d.setAmount(new BigDecimal(amount));
        d.setStatus(DonationStatus.PAID);
        d.setPaymentMode(PaymentMode.UPI);
        d.setPaymentReference("pay_TestRef123");
        d.setReceiptNumber("NGOC/2026-27/000042");
        d.setCreatedAt(Instant.parse("2026-09-03T10:15:30Z"));
        return d;
    }

    private static Ngo ngo() {
        Ngo n = new Ngo();
        n.setNgoName("Hope Foundation");
        n.setRegistrationNumber("REG/2019/0442");
        n.setPanNumber("AABCH1234K");
        n.setNgoDarpanId("MH/2019/0231456");
        n.setAddress("14 Gandhi Road, Pune 411001");
        return n;
    }

    /** The document must open as a real PDF and carry every field an 80G claim depends on. */
    @Test
    void generatedReceiptIsAValidPdfContainingEveryRequiredField() throws Exception {
        byte[] pdf = service.generatePdf(paidDonation("1500.50"), "Asha Menon", "asha@example.com", ngo());

        // Reading it back with PDFBox proves it parses — a corrupt file would throw here.
        String text;
        try (PDDocument doc = PDDocument.load(pdf)) {
            assertEquals(1, doc.getNumberOfPages());
            text = new PDFTextStripper().getText(doc);
        }

        assertTrue(text.contains("NGOC/2026-27/000042"), "receipt number missing");
        assertTrue(text.contains("Asha Menon"), "donor name missing");
        assertTrue(text.contains("asha@example.com"), "donor email missing");
        assertTrue(text.contains("Hope Foundation"), "NGO name missing");
        assertTrue(text.contains("AABCH1234K"), "NGO PAN missing — required for an 80G claim");
        assertTrue(text.contains("REG/2019/0442"), "registration number missing");
        assertTrue(text.contains("MH/2019/0231456"), "Darpan ID missing");
        assertTrue(text.contains("1500.50"), "amount in figures missing");
        assertTrue(text.contains("Section 80G"), "the 80G declaration is the point of the document");
        assertTrue(text.contains("One Thousand Five Hundred"), "amount in words missing");
    }

    /**
     * A name with characters outside Latin-1 must not stop a donor getting their receipt. PDFBox's
     * built-in fonts cannot encode them, so the service replaces them rather than throwing.
     */
    @Test
    void nonLatinCharactersInNamesDoNotBreakGeneration() throws Exception {
        Ngo n = ngo();
        n.setNgoName("आशा फाउंडेशन Trust");

        byte[] pdf = service.generatePdf(paidDonation("500"), "Ravi — Kumar", "r@example.com", n);

        try (PDDocument doc = PDDocument.load(pdf)) {
            assertEquals(1, doc.getNumberOfPages());
        }
    }

    /** An unpaid donation has no receipt number and must not be certified. */
    @Test
    void donationWithoutAReceiptNumberIsRefused() {
        Donation unpaid = paidDonation("100");
        unpaid.setReceiptNumber(null);

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.generatePdf(unpaid, "A", "a@example.com", ngo()));
    }

    // ── The words converter ───────────────────────────────────────────────────

    @Test
    void wholeRupees() {
        assertEquals("Rupees Five Hundred Only", service.rupeesInWords(new BigDecimal("500")));
    }

    @Test
    void rupeesAndPaise() {
        assertEquals("Rupees One Thousand Five Hundred and Fifty Paise Only",
                service.rupeesInWords(new BigDecimal("1500.50")));
    }

    /** "and" belongs before a trailing remainder, and nowhere else. */
    @Test
    void andAppearsOnlyBeforeARemainder() {
        assertEquals("Rupees Five Hundred and Fifty Only", service.rupeesInWords(new BigDecimal("550")));
        assertEquals("Rupees Five Hundred Only", service.rupeesInWords(new BigDecimal("500")));
    }

    /** The teens are irregular in English and are the classic place these converters break. */
    @Test
    void irregularTeens() {
        assertEquals("Rupees Nineteen Only", service.rupeesInWords(new BigDecimal("19")));
        assertEquals("Rupees Fifteen Thousand Only", service.rupeesInWords(new BigDecimal("15000")));
    }

    /**
     * The heart of it: Indian grouping. 1,00,000 is a lakh and 1,00,00,000 is a crore, so this must
     * NOT read as "twelve million".
     */
    @Test
    void indianLakhAndCroreGrouping() {
        assertEquals("Rupees One Lakh Only", service.rupeesInWords(new BigDecimal("100000")));
        assertEquals("Rupees One Crore Only", service.rupeesInWords(new BigDecimal("10000000")));
        assertEquals("Rupees One Crore Twenty Three Lakh Forty Five Thousand Six Hundred and Seventy Eight Only",
                service.rupeesInWords(new BigDecimal("12345678")));
    }
}
