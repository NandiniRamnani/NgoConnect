package app.service;

import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Razorpay's server-to-server report of what actually happened to a payment, and the safety net
 * under both Checkout flows.
 *
 * THE PROBLEM THIS SOLVES
 * Until now the only thing that could turn a payment into money in our database was the browser
 * calling /verify from Checkout's success handler. That works right up until the browser does not
 * make it back: the tab is closed on the "processing" screen, the phone drops off wifi mid-redirect,
 * the laptop sleeps, the user is on a UPI app that never returns cleanly. The money left the
 * donor's account and sits at Razorpay, while our row stays PENDING forever — a wallet top-up that
 * never appears, or an NGO never credited for a donation that really was paid.
 *
 * Razorpay tells us directly instead, from their servers to ours, with no browser in the path. So
 * the browser becomes the fast path and this becomes the guarantee. Whichever arrives first does
 * the work; the other finds the row already settled and does nothing.
 *
 * WHY THE SIGNATURE IS THE WHOLE SECURITY MODEL
 * This endpoint cannot require a login — Razorpay has no account here. It is a public URL that
 * credits wallets, so without proof of origin anyone who guessed it could POST themselves money.
 * Razorpay signs the exact bytes of the request body with HMAC-SHA256 keyed by the webhook secret,
 * a secret shared only between their dashboard and our .env. Recomputing that HMAC over the body
 * we received and getting the same answer proves two things at once: it came from Razorpay, and
 * nobody edited the amounts on the way.
 *
 * That is also why {@link #handle} takes the raw body as a String rather than a parsed object.
 * Jackson would happily give us a DTO, but re-serialising it back to JSON would reorder keys and
 * change whitespace, and the HMAC is over bytes — it would never match again. The body must be
 * hashed exactly as it arrived, then parsed.
 */
@Service
public class PaymentWebhookService {

    private final WalletService walletService;
    private final DonationService donationService;

    /** Set from the Razorpay dashboard when the webhook is created. Blank disables the endpoint. */
    @Value("${razorpay.webhook-secret:}")
    private String webhookSecret;

    public PaymentWebhookService(WalletService walletService, DonationService donationService) {
        this.walletService = walletService;
        this.donationService = donationService;
    }

    /**
     * Verify, then apply. Returns a short string describing what was done, purely for the log and
     * for the Razorpay dashboard's delivery view — the body of a webhook response is never read by
     * anything that matters, only the status code is.
     *
     * @param rawBody   the request body byte-for-byte as received; see the class note
     * @param signature the X-Razorpay-Signature header
     */
    public String handle(String rawBody, String signature) {
        /*
         * Fail closed. An unset secret means the deployment has not configured webhooks, and the
         * only safe reading of "I cannot check this signature" is to refuse — the alternative,
         * treating a blank secret as "skip verification", would turn a forgotten .env line into an
         * open endpoint that credits arbitrary wallets. 503 rather than 401 because the fault is
         * ours, not the caller's, and it tells Razorpay to retry later once we are configured.
         */
        if (webhookSecret == null || webhookSecret.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Webhooks are not configured");

        if (signature == null || signature.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing signature");

        boolean valid;
        try {
            valid = Utils.verifyWebhookSignature(rawBody, signature, webhookSecret);
        } catch (RazorpayException e) {
            valid = false;
        }
        if (!valid)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook signature");

        JSONObject body;
        try {
            body = new JSONObject(rawBody);
        } catch (JSONException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed webhook body");
        }

        String event = body.optString("event", "");

        /*
         * Only payment.captured is acted on, and the omission of payment.failed is deliberate
         * rather than unfinished.
         *
         * One Razorpay order can carry several payment attempts — a card declines, the donor
         * retries on UPI and succeeds. Both attempts fire webhooks against the SAME order id. If a
         * payment.failed handler marked the row FAILED, the successful retry moments later would
         * find a row that is no longer PENDING and refuse to credit it, so a donor who paid on
         * their second try would lose their money. A failed attempt genuinely means nothing about
         * the order; only a capture does. Rows for orders nobody ever pays simply stay PENDING,
         * which is the honest description of them.
         */
        if (!"payment.captured".equals(event)) return "ignored:" + event;

        JSONObject payment;
        try {
            payment = body.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
        } catch (JSONException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook body missing payment entity");
        }

        String orderId = payment.optString("order_id", null);
        String paymentId = payment.optString("id", null);
        String method = payment.optString("method", null);
        long amountPaise = payment.optLong("amount", -1L);

        if (orderId == null || paymentId == null || amountPaise < 0) return "ignored:incomplete-payload";

        /*
         * One order id belongs to exactly one of the two flows — it was minted either by
         * WalletService.createTopUpOrder or by DonationService.createOrder — so trying the wallet
         * first and the donation second cannot double-apply anything. Each call returns false when
         * the order is not its own, or when the row is already settled, which is the ordinary case
         * for a webhook that lost the race to the browser.
         */
        if (walletService.completeTopUpFromWebhook(orderId, paymentId, amountPaise))
            return "credited:wallet-topup:" + orderId;

        if (donationService.completeDonationFromWebhook(orderId, paymentId, method, amountPaise))
            return "credited:donation:" + orderId;

        // Nothing to do: already handled by the browser, an amount mismatch we refused to credit,
        // or an order this deployment does not know about (a shared test key, most likely).
        return "no-op:" + orderId;
    }
}
