package app.controller;

import app.dto.CreateOrderResponse;
import app.dto.DonationRequest;
import app.dto.VerifyPaymentRequest;
import app.model.Donation;
import app.service.DonationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/donations")
public class DonationController {
    private final DonationService donationService;
    public DonationController(DonationService donationService) { this.donationService = donationService; }

    /** Step 1: reserve a Razorpay order before Checkout opens. Nothing has been paid yet. */
    @PostMapping("/create-order")
    public CreateOrderResponse createOrder(@RequestBody DonationRequest request, Authentication authentication) {
        return donationService.createOrder(authentication.getName(), request);
    }

    /** Step 2: called after Checkout reports success, to cryptographically confirm the payment really happened. */
    @PostMapping("/verify")
    public Donation verifyPayment(@RequestBody VerifyPaymentRequest request, Authentication authentication) {
        return donationService.verifyPayment(authentication.getName(), request);
    }

    /**
     * The one-step alternative: pay straight from the donor's NGOConnect wallet balance.
     * No Razorpay, so no create-order and no verify — the donation comes back already PAID,
     * or the call fails with 400 because the balance was too low.
     */
    @PostMapping("/wallet")
    public Donation donateFromWallet(@RequestBody DonationRequest request, Authentication authentication) {
        return donationService.donateFromWallet(authentication.getName(), request);
    }

    @GetMapping public List<Donation> findForUser(Authentication authentication) { return donationService.findForUser(authentication.getName()); }

    /**
     * Download the 80G tax receipt for one donation, as a PDF.
     *
     * The two headers are what make a browser save a file instead of trying to display bytes:
     * APPLICATION_PDF tells it what this is, and CONTENT_DISPOSITION with `attachment` tells it to
     * download rather than render, using the filename given. Without them the response is just an
     * unlabelled blob.
     *
     * The filename has the slashes swapped for dashes because NGOC/2026-27/000042 would otherwise
     * look like a folder path to the operating system saving it.
     */
    @GetMapping("/{donationId}/receipt")
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable String donationId, Authentication authentication) {
        byte[] pdf = donationService.receiptPdf(authentication.getName(), donationId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"ngoconnect-receipt-" + donationId + ".pdf\"")
                .body(pdf);
    }
}
