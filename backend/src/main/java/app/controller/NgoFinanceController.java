package app.controller;

import app.dto.PayoutMethodRequest;
import app.dto.WithdrawalCreateRequest;
import app.model.Ngo;
import app.model.NgoBalance;
import app.model.NgoLedgerEntry;
import app.model.NgoPayoutMethod;
import app.model.WithdrawalRequest;
import app.repository.NgoRepository;
import app.service.NgoBalanceService;
import app.service.PayoutMethodService;
import app.service.WithdrawalService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

/**
 * What an NGO sees about its own money: the balance, the passbook, where payouts go, and its
 * withdrawal history.
 *
 * OWNERSHIP IS CHECKED ON EVERY CALL. The {@code ngoId} in the path is supplied by the caller,
 * so it proves nothing — {@code assertOwner} compares it against the email Spring Security
 * verified. Without that, any logged-in NGO could read another NGO's finances by editing the URL.
 * SecurityConfig restricts this whole subtree to ROLE_NGO; this is the second, per-record check.
 *
 * Note what never appears in these responses: the full bank account number. NgoPayoutMethod
 * marks it @JsonIgnore, so the NGO gets back the masked form it can recognise but not re-read.
 */
@RestController
@RequestMapping("/api/ngos/{ngoId}/finance")
public class NgoFinanceController {

    private final NgoBalanceService balanceService;
    private final PayoutMethodService payoutMethodService;
    private final WithdrawalService withdrawalService;
    private final NgoRepository ngoRepository;

    public NgoFinanceController(NgoBalanceService balanceService,
                                PayoutMethodService payoutMethodService,
                                WithdrawalService withdrawalService,
                                NgoRepository ngoRepository) {
        this.balanceService = balanceService;
        this.payoutMethodService = payoutMethodService;
        this.withdrawalService = withdrawalService;
        this.ngoRepository = ngoRepository;
    }

    /** The three buckets. Creates an all-zero balance on first visit, so this never 404s. */
    @GetMapping("/balance")
    public NgoBalance balance(@PathVariable String ngoId, Authentication authentication) {
        assertOwner(ngoId, authentication);
        return balanceService.getOrCreate(ngoId);
    }

    /** The passbook: donations received, funds cleared, withdrawals reserved and paid. */
    @GetMapping("/ledger")
    public List<NgoLedgerEntry> ledger(@PathVariable String ngoId, Authentication authentication) {
        assertOwner(ngoId, authentication);
        return balanceService.getLedger(ngoId);
    }

    /** Current payout destination, masked. Returns 204 when none has been set up yet. */
    @GetMapping("/payout-method")
    public NgoPayoutMethod payoutMethod(@PathVariable String ngoId, Authentication authentication) {
        assertOwner(ngoId, authentication);
        return payoutMethodService.find(ngoId).orElse(null);
    }

    /** Add or replace the payout destination. Starts the cooling-off window and emails the NGO. */
    @PutMapping("/payout-method")
    public NgoPayoutMethod savePayoutMethod(@PathVariable String ngoId,
                                            @RequestBody PayoutMethodRequest request,
                                            Authentication authentication) {
        Ngo ngo = assertOwner(ngoId, authentication);
        return payoutMethodService.save(ngoId, ngo.getEmail(), ngo.getNgoName(), request);
    }

    @GetMapping("/withdrawals")
    public List<WithdrawalRequest> withdrawals(@PathVariable String ngoId, Authentication authentication) {
        assertOwner(ngoId, authentication);
        return withdrawalService.findForNgo(ngoId);
    }

    /** Ask to be paid out. Reserves the money immediately so it cannot be requested twice. */
    @PostMapping("/withdrawals")
    public WithdrawalRequest requestWithdrawal(@PathVariable String ngoId,
                                               @RequestBody WithdrawalCreateRequest request,
                                               Authentication authentication) {
        assertOwner(ngoId, authentication);
        return withdrawalService.request(ngoId, authentication.getName(), request);
    }

    /**
     * The path says which NGO; Spring Security says who is calling. They must agree.
     * Being ROLE_NGO only means "some approved NGO" — it does not say which one.
     */
    private Ngo assertOwner(String ngoId, Authentication authentication) {
        Ngo ngo = ngoRepository.findById(ngoId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "NGO not found"));
        if (!ngo.getEmail().equalsIgnoreCase(authentication.getName()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This is not your NGO");
        return ngo;
    }
}
