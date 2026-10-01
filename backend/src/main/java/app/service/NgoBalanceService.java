package app.service;

import app.enums.NgoLedgerEntryType;
import app.model.NgoBalance;
import app.model.NgoLedgerEntry;
import app.repository.NgoLedgerEntryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The single owner of every NGO balance, exactly as WalletService is for donors. Nothing else
 * changes a bucket — WithdrawalService and DonationService ask this class to do it.
 *
 * Every movement here is a conditional {@code findAndModify}, for the same reason as
 * {@link WalletService#debitForDonation}: reading a balance into Java, deciding, and writing it
 * back leaves a gap two simultaneous requests can both slip through. Keeping the condition
 * inside the update makes MongoDB serialise them on the one document.
 */
@Service
public class NgoBalanceService {

    private final NgoLedgerEntryRepository ledgerRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * How long donated money sits in `clearing` before it can be withdrawn.
     *
     * This exists because a donation being marked PAID does not mean the platform's bank account
     * has the money — Razorpay settles to us on a delay (T+2 by default). Paying an NGO sooner
     * means paying out money we do not physically hold yet. Set to 0 to make donations
     * withdrawable immediately, which is useful when demonstrating the flow.
     */
    @Value("${payouts.clearing-hours:48}")
    private long clearingHours;

    public NgoBalanceService(NgoLedgerEntryRepository ledgerRepository, MongoTemplate mongoTemplate) {
        this.ledgerRepository = ledgerRepository;
        this.mongoTemplate = mongoTemplate;
    }

    // ── Reading ───────────────────────────────────────────────────────────────

    /**
     * Fetch an NGO's balance, creating an all-zero one on first access.
     * Upsert rather than find-then-insert, so two simultaneous first reads cannot create two
     * documents. {@code setOnInsert} applies only to a brand-new document, so real balances are
     * never zeroed by this call.
     */
    public NgoBalance getOrCreate(String ngoId) {
        Instant now = Instant.now();
        return mongoTemplate.findAndModify(
                Query.query(Criteria.where("ngoId").is(ngoId)),
                new Update()
                        .setOnInsert("ngoId", ngoId)
                        .setOnInsert("clearingPaise", 0L)
                        .setOnInsert("availablePaise", 0L)
                        .setOnInsert("reservedPaise", 0L)
                        .setOnInsert("lifetimeReceivedPaise", 0L)
                        .setOnInsert("lifetimeWithdrawnPaise", 0L)
                        .setOnInsert("createdAt", now)
                        .set("updatedAt", now),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                NgoBalance.class);
    }

    public List<NgoLedgerEntry> getLedger(String ngoId) {
        return ledgerRepository.findByNgoIdOrderByCreatedAtDesc(ngoId);
    }

    // ── Money coming in, from DonationService ─────────────────────────────────

    /**
     * A donation was paid. The money lands in `clearing`, NOT in `available` — the NGO cannot
     * touch it until the settlement window passes and {@link #releaseClearedFunds()} promotes it.
     *
     * Called for BOTH funding routes. A wallet-funded donation was in fact collected earlier
     * (when the donor topped up), so applying the same window to it is conservative rather than
     * exact; one code path is worth more here than a few hours of precision.
     */
    public void creditFromDonation(String ngoId, long amountPaise, String donationId, String donorLabel) {
        Instant now = Instant.now();

        mongoTemplate.findAndModify(
                Query.query(Criteria.where("ngoId").is(ngoId)),
                new Update()
                        .inc("clearingPaise", amountPaise)
                        .inc("lifetimeReceivedPaise", amountPaise)
                        .set("updatedAt", now),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                NgoBalance.class);

        NgoLedgerEntry entry = new NgoLedgerEntry();
        entry.setNgoId(ngoId);
        entry.setType(NgoLedgerEntryType.DONATION_RECEIVED);
        entry.setAmountPaise(amountPaise);
        entry.setDescription("Donation received" + (donorLabel == null ? "" : " from " + donorLabel));
        entry.setDonationId(donationId);
        entry.setClearsAt(now.plus(Duration.ofHours(clearingHours)));
        entry.setCleared(false);
        entry.setCreatedAt(now);
        ledgerRepository.save(entry);
    }

    // ── The clearing job: clearing -> available ───────────────────────────────

    /**
     * Promotes every donation whose settlement window has expired. Runs every five minutes;
     * the interval does not need to be tight because the window is measured in days.
     *
     * Each row is CLAIMED before its money moves: the findAndModify below only matches while
     * {@code cleared} is still false, so if two application instances run this job at the same
     * time, exactly one of them wins each row and the money is promoted once. This is the same
     * compare-and-set that stops a top-up being credited twice.
     */
    @Scheduled(fixedDelayString = "${payouts.clearing-job-ms:300000}")
    public void releaseClearedFunds() {
        List<NgoLedgerEntry> due = ledgerRepository
                .findByClearedFalseAndClearsAtLessThanEqual(Instant.now());

        for (NgoLedgerEntry entry : due) {
            NgoLedgerEntry claimed = mongoTemplate.findAndModify(
                    Query.query(Criteria.where("_id").is(entry.getId()).and("cleared").is(false)),
                    new Update().set("cleared", true),
                    FindAndModifyOptions.options().returnNew(true),
                    NgoLedgerEntry.class);

            if (claimed == null) continue; // another instance already promoted this row

            NgoBalance balance = mongoTemplate.findAndModify(
                    Query.query(Criteria.where("ngoId").is(entry.getNgoId())
                            .and("clearingPaise").gte(entry.getAmountPaise())),
                    new Update()
                            .inc("clearingPaise", -entry.getAmountPaise())
                            .inc("availablePaise", entry.getAmountPaise())
                            .set("updatedAt", Instant.now()),
                    FindAndModifyOptions.options().returnNew(true),
                    NgoBalance.class);

            if (balance == null) continue; // clearing bucket was short — leave it for inspection

            writeEntry(entry.getNgoId(), NgoLedgerEntryType.CLEARED, entry.getAmountPaise(),
                    balance.getAvailablePaise(), "Funds cleared and available to withdraw",
                    entry.getDonationId(), null);
        }
    }

    // ── Money moving for a withdrawal, from WithdrawalService ─────────────────

    /**
     * Lock an amount for an open withdrawal request: available -> reserved.
     * Returns the updated balance, or null when `available` was short and nothing changed.
     *
     * This is the NGO-side twin of {@code WalletService.debit}. Without the {@code gte}
     * condition inside the query, an NGO could open two requests for their whole balance at the
     * same instant and the admin would see two approvable requests for money that only exists
     * once.
     */
    public NgoBalance reserve(String ngoId, long amountPaise, String withdrawalId) {
        NgoBalance balance = mongoTemplate.findAndModify(
                Query.query(Criteria.where("ngoId").is(ngoId)
                        .and("availablePaise").gte(amountPaise)),
                new Update()
                        .inc("availablePaise", -amountPaise)
                        .inc("reservedPaise", amountPaise)
                        .set("updatedAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true),
                NgoBalance.class);

        if (balance == null) return null;

        writeEntry(ngoId, NgoLedgerEntryType.WITHDRAWAL_RESERVED, amountPaise,
                balance.getAvailablePaise(), "Withdrawal requested", null, withdrawalId);
        return balance;
    }

    /** The transfer happened: the money leaves `reserved` permanently. */
    public NgoBalance settle(String ngoId, long amountPaise, String withdrawalId) {
        NgoBalance balance = mongoTemplate.findAndModify(
                Query.query(Criteria.where("ngoId").is(ngoId)
                        .and("reservedPaise").gte(amountPaise)),
                new Update()
                        .inc("reservedPaise", -amountPaise)
                        .inc("lifetimeWithdrawnPaise", amountPaise)
                        .set("updatedAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true),
                NgoBalance.class);

        if (balance == null) return null;

        writeEntry(ngoId, NgoLedgerEntryType.WITHDRAWAL_PAID, amountPaise,
                balance.getAvailablePaise(), "Withdrawal paid out", null, withdrawalId);
        return balance;
    }

    /** Rejected or bounced: reserved -> available, so the NGO can request it again. */
    public NgoBalance release(String ngoId, long amountPaise, String withdrawalId, String reason) {
        NgoBalance balance = mongoTemplate.findAndModify(
                Query.query(Criteria.where("ngoId").is(ngoId)
                        .and("reservedPaise").gte(amountPaise)),
                new Update()
                        .inc("reservedPaise", -amountPaise)
                        .inc("availablePaise", amountPaise)
                        .set("updatedAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true),
                NgoBalance.class);

        if (balance == null) return null;

        writeEntry(ngoId, NgoLedgerEntryType.WITHDRAWAL_REVERSED, amountPaise,
                balance.getAvailablePaise(), reason, null, withdrawalId);
        return balance;
    }

    // ── Shared ────────────────────────────────────────────────────────────────

    private void writeEntry(String ngoId, NgoLedgerEntryType type, long amountPaise,
                            long availableAfter, String description,
                            String donationId, String withdrawalId) {
        NgoLedgerEntry entry = new NgoLedgerEntry();
        entry.setNgoId(ngoId);
        entry.setType(type);
        entry.setAmountPaise(amountPaise);
        entry.setAvailableAfterPaise(availableAfter);
        entry.setDescription(description);
        entry.setDonationId(donationId);
        entry.setWithdrawalId(withdrawalId);
        entry.setCleared(true); // only DONATION_RECEIVED rows are ever pending clearance
        entry.setCreatedAt(Instant.now());
        ledgerRepository.save(entry);
    }
}
