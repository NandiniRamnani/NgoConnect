package app.controller;

import app.service.PaymentWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where Razorpay's servers report what happened to a payment. Not called by our frontend at all —
 * the URL goes into the Razorpay dashboard under Settings -> Webhooks.
 *
 * Unauthenticated by necessity: Razorpay has no login here. SecurityConfig permits it explicitly,
 * and PaymentWebhookService rejects anything without a valid signature, which is what stands in
 * for authentication. See that class for why the signature is sufficient and how it is checked.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentWebhookController {

    private final PaymentWebhookService webhookService;

    public PaymentWebhookController(PaymentWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /**
     * The body is bound as a raw String, NOT as a DTO, and this is load-bearing rather than lazy:
     * the signature is an HMAC over the exact bytes Razorpay sent, so the moment Jackson parses and
     * anything re-serialises it, key order and whitespace shift and the hash stops matching. It has
     * to be hashed as received. The service parses it afterwards, once it is proven genuine.
     *
     * Returning 200 for events we deliberately ignore matters too. Razorpay retries any delivery
     * that does not get a 2xx, with backoff, for hours — answering "not my event" with an error
     * would earn us the same irrelevant payload over and over. A non-2xx here should mean "this
     * failed, please do send it again", which is exactly what the signature and parse failures
     * thrown by the service mean.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleRazorpayWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        return ResponseEntity.ok(webhookService.handle(rawBody, signature));
    }
}
