package app.controller;

import app.dto.PayoutDestinationResponse;
import app.dto.WithdrawalDecisionRequest;
import app.enums.WithdrawalStatus;
import app.model.WithdrawalRequest;
import app.service.PayoutMethodService;
import app.service.WithdrawalService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/**
 * The admin's payout queue. Everything under /api/admin/** already requires ROLE_ADMIN via
 * SecurityConfig, so there is no per-record ownership check here — an admin legitimately sees
 * every NGO's requests.
 *
 * {@code reviewedBy} is taken from {@code authentication.getName()} on every action, so the
 * audit trail records who actually made each decision rather than trusting the request body.
 */
@RestController
@RequestMapping("/api/admin/withdrawals")
public class AdminWithdrawalController {

    private final WithdrawalService withdrawalService;
    private final PayoutMethodService payoutMethodService;

    public AdminWithdrawalController(WithdrawalService withdrawalService,
                                     PayoutMethodService payoutMethodService) {
        this.withdrawalService = withdrawalService;
        this.payoutMethodService = payoutMethodService;
    }

    /** The queue, filtered by status. Defaults to the ones actually waiting on the admin. */
    @GetMapping
    public List<WithdrawalRequest> list(
            @RequestParam(defaultValue = "REQUESTED") WithdrawalStatus status) {
        return withdrawalService.findByStatus(status);
    }

    /** Badge count for the admin dashboard — how many are waiting right now. */
    @GetMapping("/pending-count")
    public Map<String, Long> pendingCount() {
        return Map.of("count", withdrawalService.countPending());
    }

    /**
     * The full bank details for one request, including the unmasked account number.
     *
     * This is the ONE endpoint in the system that reveals it, and it exists because the admin
     * physically cannot make a NEFT transfer without the number. It reads from the snapshot
     * frozen onto the request, not from the NGO's current payout method — so if the NGO edited
     * their details after requesting, the admin still sees what was actually approved.
     */
    @GetMapping("/{id}/payout-destination")
    public PayoutDestinationResponse payoutDestination(@PathVariable String id) {
        WithdrawalRequest request = withdrawalService.findById(id);
        return new PayoutDestinationResponse(
                request.getPayoutType(),
                request.getPayoutAccountHolderName(),
                request.getPayoutAccountNumber(),
                request.getPayoutIfsc(),
                request.getPayoutBankName(),
                request.getPayoutUpiId());
    }

    /** REQUESTED -> APPROVED. Nothing has been transferred yet; the money stays reserved. */
    @PatchMapping("/{id}/approve")
    public WithdrawalRequest approve(@PathVariable String id,
                                     @RequestBody(required = false) WithdrawalDecisionRequest body,
                                     Authentication authentication) {
        return withdrawalService.approve(id, authentication.getName(), body);
    }

    /** REQUESTED -> REJECTED. Requires a reason; the money goes back to available. */
    @PatchMapping("/{id}/reject")
    public WithdrawalRequest reject(@PathVariable String id,
                                    @RequestBody WithdrawalDecisionRequest body,
                                    Authentication authentication) {
        return withdrawalService.reject(id, authentication.getName(), body);
    }

    /** APPROVED -> PAID. Requires the transfer reference (UTR) the admin got from their bank. */
    @PatchMapping("/{id}/paid")
    public WithdrawalRequest markPaid(@PathVariable String id,
                                      @RequestBody WithdrawalDecisionRequest body,
                                      Authentication authentication) {
        return withdrawalService.markPaid(id, authentication.getName(), body);
    }

    /** APPROVED -> FAILED, when the transfer bounced. The money returns to available. */
    @PatchMapping("/{id}/failed")
    public WithdrawalRequest markFailed(@PathVariable String id,
                                        @RequestBody(required = false) WithdrawalDecisionRequest body,
                                        Authentication authentication) {
        return withdrawalService.markFailed(id, authentication.getName(), body);
    }

    /** Record that the admin has checked an NGO's details against its registration documents. */
    @PatchMapping("/payout-methods/{ngoId}/verify")
    public void verifyPayoutMethod(@PathVariable String ngoId) {
        payoutMethodService.markVerified(ngoId);
    }
}
