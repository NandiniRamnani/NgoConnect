package app.controller;

import app.dto.TopUpOrderResponse;
import app.dto.TopUpRequest;
import app.dto.VerifyPaymentRequest;
import app.model.Wallet;
import app.model.WalletTransaction;
import app.service.WalletService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * HTTP surface of the wallet. Every method is a thin translation layer: pull the caller's
 * identity out of Authentication, hand it plus the request body to WalletService, return
 * whatever comes back. No business logic, no balance arithmetic — that all lives in the service.
 *
 * NOTE ON IDENTITY: nothing here reads a userId from the request body. Spring Security has
 * already authenticated the caller, and authentication.getName() is the email it verified.
 * If a userId came from the body instead, anyone could top up or spend from someone else's
 * wallet just by changing a value in the JSON.
 *
 * SecurityConfig restricts /api/wallet/** to ROLE_USER, so an NGO login cannot reach any of this.
 */
@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    /** Current balance. Creates an empty wallet on first visit, so this never 404s. */
    @GetMapping
    public Wallet getWallet(Authentication authentication) {
        return walletService.getWalletForEmail(authentication.getName());
    }

    /** The passbook: every top-up, donation and refund, newest first. */
    @GetMapping("/transactions")
    public List<WalletTransaction> getTransactions(Authentication authentication) {
        return walletService.getTransactionsForEmail(authentication.getName());
    }

    /** Top-up step 1 — reserve a Razorpay order. Nothing has been paid or credited yet. */
    @PostMapping("/topup/create-order")
    public TopUpOrderResponse createTopUpOrder(@RequestBody TopUpRequest request, Authentication authentication) {
        return walletService.createTopUpOrder(authentication.getName(), request);
    }

    /** Top-up step 2 — prove the payment really happened, then credit the balance. */
    @PostMapping("/topup/verify")
    public Wallet verifyTopUp(@RequestBody VerifyPaymentRequest request, Authentication authentication) {
        return walletService.verifyTopUp(authentication.getName(), request);
    }
}
