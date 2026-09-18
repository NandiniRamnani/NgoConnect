package app.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The webhook is the one endpoint no human ever exercises by hand — it is called by Razorpay's
 * servers against a public URL, and a mistake in it is invisible until money quietly stops being
 * credited. So the things worth pinning down are the ones that fail silently:
 *
 *  - that we hash the body the same way Razorpay does (an argument in the wrong order in the SDK
 *    call would reject every genuine callback, and no test user would ever notice)
 *  - that a forged body is refused
 *  - that a captured payment is routed to the right flow
 *  - that an unset secret refuses everything instead of trusting everyone
 */
class PaymentWebhookServiceTest {

    private static final String SECRET = "test_webhook_secret";

    private WalletService walletService;
    private DonationService donationService;
    private PaymentWebhookService service;

    @BeforeEach
    void setUp() {
        walletService = mock(WalletService.class);
        donationService = mock(DonationService.class);
        service = new PaymentWebhookService(walletService, donationService);
        ReflectionTestUtils.setField(service, "webhookSecret", SECRET);
    }

    /**
     * Independently reimplements what Razorpay does before sending: HMAC-SHA256 over the raw body,
     * keyed with the shared webhook secret, hex encoded. Written out longhand rather than reusing
     * the Razorpay SDK, so the test is checking our integration against the documented algorithm
     * rather than against the same helper it is meant to be validating.
     */
    private static String sign(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private static String capturedBody(String orderId, String paymentId, long amountPaise) {
        return """
               {"event":"payment.captured","payload":{"payment":{"entity":{
                 "id":"%s","order_id":"%s","amount":%d,"method":"upi","currency":"INR"}}}}
               """.formatted(paymentId, orderId, amountPaise);
    }

    @Test
    void genuinelySignedCapture_creditsTheWalletTopUp() throws Exception {
        String body = capturedBody("order_ABC", "pay_XYZ", 50000L);
        when(walletService.completeTopUpFromWebhook("order_ABC", "pay_XYZ", 50000L)).thenReturn(true);

        String result = service.handle(body, sign(body));

        assertTrue(result.startsWith("credited:wallet-topup"), result);
        verify(walletService).completeTopUpFromWebhook("order_ABC", "pay_XYZ", 50000L);
        // The wallet claimed it, so the donation flow must not also be offered the same order.
        verify(donationService, never()).completeDonationFromWebhook(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void orderThatIsNotAWalletTopUp_fallsThroughToTheDonationFlow() throws Exception {
        String body = capturedBody("order_DON", "pay_DON", 100000L);
        when(walletService.completeTopUpFromWebhook(anyString(), anyString(), anyLong())).thenReturn(false);
        when(donationService.completeDonationFromWebhook("order_DON", "pay_DON", "upi", 100000L)).thenReturn(true);

        assertTrue(service.handle(body, sign(body)).startsWith("credited:donation"));
    }

    /** The whole point of the signature: a body nobody could have signed must not move money. */
    @Test
    void forgedBody_isRejectedAndCreditsNothing() throws Exception {
        String real = capturedBody("order_ABC", "pay_XYZ", 100L);
        String forged = capturedBody("order_ABC", "pay_XYZ", 10_000_000L); // attacker inflates the amount

        // Signature of the real body, attached to a body claiming a far larger payment.
        assertThrows(ResponseStatusException.class, () -> service.handle(forged, sign(real)));

        verify(walletService, never()).completeTopUpFromWebhook(anyString(), anyString(), anyLong());
        verify(donationService, never()).completeDonationFromWebhook(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void missingSignatureHeader_isRejected() {
        assertThrows(ResponseStatusException.class,
                () -> service.handle(capturedBody("order_ABC", "pay_XYZ", 100L), null));
    }

    /** A deployment that forgot to configure the secret must refuse, not wave everything through. */
    @Test
    void unconfiguredSecret_refusesEvenACorrectlySignedBody() throws Exception {
        String body = capturedBody("order_ABC", "pay_XYZ", 100L);
        String signature = sign(body);
        ReflectionTestUtils.setField(service, "webhookSecret", "");

        assertThrows(ResponseStatusException.class, () -> service.handle(body, signature));
        verify(walletService, never()).completeTopUpFromWebhook(anyString(), anyString(), anyLong());
    }

    /**
     * A failed attempt must NOT settle the row: the same order can still be paid on a retry, and
     * marking it failed here would make that retry uncreditable.
     */
    @Test
    void paymentFailedEvent_isAcknowledgedButChangesNothing() throws Exception {
        String body = """
                      {"event":"payment.failed","payload":{"payment":{"entity":{
                        "id":"pay_F","order_id":"order_F","amount":100,"method":"card"}}}}
                      """;

        assertEquals("ignored:payment.failed", service.handle(body, sign(body)));
        verify(walletService, never()).completeTopUpFromWebhook(anyString(), anyString(), anyLong());
        verify(donationService, never()).completeDonationFromWebhook(anyString(), anyString(), anyString(), anyLong());
    }
}
