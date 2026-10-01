package app.service;

import app.model.Counter;
import app.model.Donation;
import app.model.Ngo;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Produces the donation receipt a donor attaches to their income tax return.
 *
 * WHY THIS MATTERS MORE THAN AN ORDINARY "THANK YOU" PDF
 * Section 80G of the Income Tax Act, 1961 lets an Indian taxpayer deduct donations made to
 * registered charities from their taxable income. To claim it they must produce a receipt carrying
 * specific things: the charity's registration details and PAN, the donor's name, the amount, and a
 * receipt number the charity can be held to. It is a tax document, not a courtesy — which is why
 * the number is sequential and stored, why the amount appears in words as well as figures (the
 * traditional guard against a digit being altered by hand), and why nothing here is generated
 * twice differently for the same donation.
 *
 * PDFBox is already a dependency — DocumentVerificationService uses it to READ text out of the
 * documents NGOs upload. The same library writes PDFs, so this adds no new dependency at all.
 */
@Service
public class ReceiptService {

    /** A4 in PDF points (1/72 inch). PDFBox measures everything in these. */
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();   // 595
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight(); // 842
    private static final float MARGIN = 50f;

    /**
     * Indian Standard Time, fixed rather than taken from the server clock's zone. A donation made
     * at 11pm in Mumbai must land in the Indian financial year everyone else agrees it belongs to,
     * even when the server is running in UTC in some other country.
     */
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMMM yyyy");

    private final MongoTemplate mongoTemplate;

    public ReceiptService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    // ── Receipt numbering ─────────────────────────────────────────────────────

    /**
     * Hand out the next receipt number, e.g. "NGOC/2026-27/000042".
     *
     * The sequence restarts each financial year, which is what Indian accounting expects: receipts
     * are filed and audited per year, so a number carries the year it belongs to and counts from 1
     * inside it. A separate counter document per year is what makes that restart automatic — the
     * first donation after 1 April simply finds no counter for the new year and starts one.
     *
     * The $inc runs inside findAndModify with upsert, so "create the counter if this is the year's
     * first receipt, add one, and tell me the result" is a single database operation. Two donations
     * completing in the same millisecond are serialised by MongoDB and get 42 and 43 — never 42
     * twice, which would put the same receipt number on two different donations.
     */
    public String nextReceiptNumber(Instant when) {
        String financialYear = financialYearOf(when);

        Counter counter = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is("receipt:" + financialYear)),
                new Update().inc("seq", 1L),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                Counter.class);

        long sequence = counter == null ? 1L : counter.getSeq();

        // Zero-padded to six digits so receipts sort correctly as text and look consistent:
        // NGOC/2026-27/000042 rather than NGOC/2026-27/42.
        return String.format("NGOC/%s/%06d", financialYear, sequence);
    }

    /**
     * The Indian financial year containing a moment, formatted "2026-27".
     *
     * India's tax year runs 1 April to 31 March, not January to December. So a donation on
     * 15 September 2026 and one on 20 February 2027 belong to the SAME year, 2026-27, while
     * 20 February 2027 and 20 April 2027 belong to different ones. Anything before April counts
     * against the previous calendar year, which is what the subtraction below expresses.
     */
    private String financialYearOf(Instant when) {
        LocalDate date = LocalDate.ofInstant(when, IST);
        int startYear = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return startYear + "-" + String.format("%02d", (startYear + 1) % 100);
    }

    // ── PDF generation ────────────────────────────────────────────────────────

    /**
     * Render the receipt and return it as raw PDF bytes, ready to be written to an HTTP response
     * or attached to an email.
     *
     * Bytes rather than a File on disk deliberately: the receipt is derived data that can always be
     * regenerated from the donation, so writing it to the server's filesystem would create
     * something to back up, clean up, and run out of space on, for no gain.
     */
    public byte[] generatePdf(Donation donation, String donorName, String donorEmail, Ngo ngo) {
        if (donation.getReceiptNumber() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This donation has no receipt number — only paid donations are certified");

        // try-with-resources: PDFBox holds native memory buffers, and closing the document is what
        // releases them. A leak here would be invisible until the server slowly ran out of memory.
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                // PDF's origin is the BOTTOM-left corner and y grows upward, the opposite of a
                // screen. Tracking a `y` cursor that starts high and decreases lets the code read
                // top-to-bottom the way the page does.
                float y = PAGE_HEIGHT - MARGIN;

                y = drawHeader(content, y);
                y = drawReceiptMeta(content, y, donation);
                y = drawParties(content, y, donorName, donorEmail, ngo);
                y = drawAmountBlock(content, y, donation);
                drawFooter(content, y, ngo);
            }

            document.save(out);
            return out.toByteArray();

        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not generate the receipt: " + e.getMessage());
        }
    }

    /** Platform name and the line that tells the reader what kind of document this is. */
    private float drawHeader(PDPageContentStream c, float y) throws Exception {
        text(c, PDType1Font.HELVETICA_BOLD, 22, MARGIN, y, "NGOConnect");
        y -= 18;
        text(c, PDType1Font.HELVETICA, 9, MARGIN, y, "Connecting verified NGOs with donors and volunteers");
        y -= 28;

        // A rule under the masthead. Drawn as a thin filled rectangle rather than a stroked line
        // because a rectangle's thickness is explicit and does not depend on the current line width.
        c.setNonStrokingColor(0.09f, 0.64f, 0.29f); // the green used across the UI
        c.addRect(MARGIN, y, PAGE_WIDTH - 2 * MARGIN, 2);
        c.fill();
        c.setNonStrokingColor(0f, 0f, 0f); // back to black, or every later draw inherits the green
        y -= 32;

        text(c, PDType1Font.HELVETICA_BOLD, 15, MARGIN, y, "DONATION RECEIPT");
        y -= 15;
        text(c, PDType1Font.HELVETICA_OBLIQUE, 8.5f, MARGIN, y,
                "Issued under Section 80G of the Income Tax Act, 1961");
        return y - 26;
    }

    /** Receipt number and issue date, the two fields that identify this document. */
    private float drawReceiptMeta(PDPageContentStream c, float y, Donation donation) throws Exception {
        LocalDate donatedOn = LocalDate.ofInstant(donation.getCreatedAt(), IST);

        labelValue(c, y, "Receipt No.", donation.getReceiptNumber());
        labelValue(c, y - 16, "Date of Donation", donatedOn.format(DATE_FORMAT));
        return y - 42;
    }

    /** Who gave, and who received — the receipt is meaningless without both. */
    private float drawParties(PDPageContentStream c, float y, String donorName, String donorEmail, Ngo ngo)
            throws Exception {
        text(c, PDType1Font.HELVETICA_BOLD, 11, MARGIN, y, "Received From");
        y -= 16;
        labelValue(c, y, "Name", blankToDash(donorName));
        labelValue(c, y - 16, "Email", blankToDash(donorEmail));
        y -= 42;

        text(c, PDType1Font.HELVETICA_BOLD, 11, MARGIN, y, "Donation Made To");
        y -= 16;
        labelValue(c, y, "Organisation", blankToDash(ngo.getNgoName()));
        labelValue(c, y - 16, "Registration No.", blankToDash(ngo.getRegistrationNumber()));
        labelValue(c, y - 32, "PAN", blankToDash(ngo.getPanNumber()));
        labelValue(c, y - 48, "NGO Darpan ID", blankToDash(ngo.getNgoDarpanId()));
        labelValue(c, y - 64, "Address", blankToDash(ngo.getAddress()));
        return y - 90;
    }

    /** The amount, boxed and repeated in words — the part an auditor actually looks at. */
    private float drawAmountBlock(PDPageContentStream c, float y, Donation donation) throws Exception {
        float boxHeight = 62;
        float boxTop = y;
        float boxBottom = y - boxHeight;

        // A very light grey panel to separate the figure from the surrounding detail.
        c.setNonStrokingColor(0.95f, 0.97f, 0.95f);
        c.addRect(MARGIN, boxBottom, PAGE_WIDTH - 2 * MARGIN, boxHeight);
        c.fill();
        c.setNonStrokingColor(0f, 0f, 0f);

        float inner = MARGIN + 14;
        text(c, PDType1Font.HELVETICA, 9, inner, boxTop - 18, "Amount Donated");
        // "Rs." rather than the ₹ symbol: the 14 standard PDF fonts are Latin-1 only and PDFBox
        // throws on a character they cannot encode. Embedding a Unicode font just for one glyph
        // would add a megabyte to every receipt.
        text(c, PDType1Font.HELVETICA_BOLD, 20, inner, boxTop - 42,
                "Rs. " + donation.getAmount().setScale(2, java.math.RoundingMode.UNNECESSARY));
        text(c, PDType1Font.HELVETICA_OBLIQUE, 9, inner, boxTop - 56,
                rupeesInWords(donation.getAmount()));

        y = boxBottom - 26;

        String mode = donation.getPaymentMode() == null ? "-" : donation.getPaymentMode().name().replace('_', ' ');
        labelValue(c, y, "Payment Mode", mode);
        labelValue(c, y - 16, "Reference", blankToDash(donation.getPaymentReference()));
        return y - 44;
    }

    /** The small print, and the honesty note about what this receipt does and does not prove. */
    private void drawFooter(PDPageContentStream c, float y, Ngo ngo) throws Exception {
        text(c, PDType1Font.HELVETICA, 8, MARGIN, y,
                "This is a computer-generated receipt and does not require a physical signature.");
        y -= 12;
        text(c, PDType1Font.HELVETICA, 8, MARGIN, y,
                "Deduction under Section 80G is subject to the organisation holding valid 80G registration");
        y -= 11;
        text(c, PDType1Font.HELVETICA, 8, MARGIN, y,
                "and to the limits and conditions in force for the relevant assessment year.");
        y -= 20;
        text(c, PDType1Font.HELVETICA_BOLD, 8, MARGIN, y,
                "Issued via NGOConnect on behalf of " + blankToDash(ngo.getNgoName()) + ".");
    }

    // ── Small drawing helpers ─────────────────────────────────────────────────

    /**
     * Draw one run of text at an absolute position.
     *
     * PDFBox has no "write a string here" call. Every piece of text must be wrapped in
     * beginText/endText, given a font and size, and positioned — so this wrapper exists to stop
     * that five-line ritual from being repeated for every label on the page.
     */
    private void text(PDPageContentStream c, PDFont font, float size, float x, float y, String value)
            throws Exception {
        c.beginText();
        c.setFont(font, size);
        c.newLineAtOffset(x, y);
        c.showText(sanitise(value));
        c.endText();
    }

    /** A grey label on the left and its bold value in a fixed column, so the fields line up. */
    private void labelValue(PDPageContentStream c, float y, String label, String value) throws Exception {
        c.setNonStrokingColor(0.4f, 0.4f, 0.4f);
        text(c, PDType1Font.HELVETICA, 9.5f, MARGIN, y, label);
        c.setNonStrokingColor(0f, 0f, 0f);
        text(c, PDType1Font.HELVETICA_BOLD, 9.5f, MARGIN + 120, y, value);
    }

    /**
     * Strip anything the built-in PDF fonts cannot encode.
     *
     * Helvetica and its siblings cover Latin-1 only. An NGO name containing Devanagari, a smart
     * quote pasted from Word, or an emoji in a caption would make showText throw and the whole
     * receipt fail to generate. Replacing unencodable characters with '?' means a slightly odd
     * name on the page instead of a donor who cannot get their tax document at all.
     */
    private String sanitise(String value) {
        if (value == null) return "-";
        StringBuilder sb = new StringBuilder(value.length());
        for (char ch : value.toCharArray()) sb.append(ch < 256 ? ch : '?');
        return sb.toString();
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    // ── Amount in words ───────────────────────────────────────────────────────

    private static final String[] ONES = {
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen",
            "Eighteen", "Nineteen"
    };

    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    /**
     * "1500.50" becomes "Rupees One Thousand Five Hundred and Fifty Paise Only".
     *
     * Writing the amount out in words is not decoration. On a paper receipt a figure can be altered
     * — a 1 becomes a 4, a zero is appended — but rewriting the words to match is far harder, so
     * the two together are a check on each other. Indian receipts have carried both for as long as
     * receipts have existed, and a tax document without it looks wrong to anyone who handles them.
     */
    public String rupeesInWords(BigDecimal amount) {
        long paise = WalletService.toPaise(amount);
        long rupees = paise / 100;
        long remainingPaise = paise % 100;

        StringBuilder words = new StringBuilder("Rupees ").append(numberToWords(rupees));
        if (remainingPaise > 0) {
            words.append(" and ").append(numberToWords(remainingPaise)).append(" Paise");
        }
        return words.append(" Only").toString();
    }

    /**
     * Indian place values, which group differently from Western ones.
     *
     * English breaks large numbers every three digits — thousand, million, billion. Indian counting
     * breaks every two after the first thousand: 1,00,000 is one lakh and 1,00,00,000 is one crore.
     * So 12345678 reads "One Crore Twenty Three Lakh Forty Five Thousand Six Hundred Seventy Eight",
     * not "Twelve Million...". Peeling the crore off first, then the lakh, then the thousand, is
     * exactly that grouping expressed as arithmetic.
     *
     * The recursive call on crore handles amounts above ninety-nine crore, where the crore count
     * itself needs the same treatment.
     */
    private String numberToWords(long n) {
        if (n == 0) return "Zero";

        StringBuilder sb = new StringBuilder();

        long crore = n / 10_000_000;
        n %= 10_000_000;
        long lakh = n / 100_000;
        n %= 100_000;
        long thousand = n / 1_000;
        n %= 1_000;
        long hundred = n / 100;
        n %= 100;

        if (crore > 0) sb.append(numberToWords(crore)).append(" Crore ");
        if (lakh > 0) sb.append(underHundred(lakh)).append(" Lakh ");
        if (thousand > 0) sb.append(underHundred(thousand)).append(" Thousand ");
        if (hundred > 0) sb.append(ONES[(int) hundred]).append(" Hundred ");

        // "and" only before a trailing remainder, so it reads "Five Hundred and Fifty" but plain
        // "Five Hundred" when nothing follows.
        if (n > 0) {
            if (sb.length() > 0) sb.append("and ");
            sb.append(underHundred(n)).append(' ');
        }

        return sb.toString().trim();
    }

    /**
     * 0-99 in words. Everything below twenty is irregular in English — "eleven", "twelve",
     * "thirteen" follow no rule — so those twenty are simply listed. From twenty up the pattern is
     * regular and a tens word plus a ones word covers every case.
     */
    private String underHundred(long n) {
        if (n < 20) return ONES[(int) n];
        String tens = TENS[(int) (n / 10)];
        long ones = n % 10;
        return ones == 0 ? tens : tens + " " + ONES[(int) ones];
    }
}
