package app.service;

import app.dto.FoodSettingsRequest;
import app.dto.FoodSlotRequest;
import app.enums.BeneficiaryGroup;
import app.enums.FoodSlotStatus;
import app.enums.MealType;
import app.model.FoodSlot;
import app.model.Ngo;
import app.repository.FoodSlotRepository;
import app.repository.NgoRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Food slots: an NGO says "nobody is feeding our children lunch tomorrow, it costs Rs.2,400", and a
 * donor pays exactly that to cover it.
 *
 * Payment itself is NOT handled here. Sponsoring a slot is an ordinary donation to the NGO that
 * carries a foodSlotId, so it goes through the same Razorpay / wallet code, credits the NGO's
 * balance the same way and gets the same 80G receipt. This class only answers "may this slot be
 * paid for?" before checkout and flips it to FILLED once the donation is PAID.
 */
@Service
public class FoodSlotService {

    /** Dates are the NGO's calendar days, and every NGO on the platform is in India. */
    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    /** How far ahead an NGO can post. Far enough to plan a month; not so far that slots go stale. */
    private static final int MAX_DAYS_AHEAD = 60;

    /** Default window for the public page when no range is asked for. */
    private static final int DEFAULT_DAYS_SHOWN = 14;

    /**
     * How long a donor who opened checkout keeps the slot to themselves. Long enough to finish a
     * UPI payment on another phone, short enough that an abandoned tab does not block the meal.
     */
    private static final Duration CHECKOUT_HOLD = Duration.ofMinutes(15);

    private static final BigDecimal MAX_SLOT_AMOUNT = new BigDecimal("1000000"); // Rs.10,00,000, same cap as a donation
    private static final BigDecimal MAX_COST_PER_PERSON = new BigDecimal("10000");
    private static final int MAX_PEOPLE = 5000;

    private final FoodSlotRepository slotRepository;
    private final NgoRepository ngoRepository;
    private final MongoTemplate mongoTemplate;

    public FoodSlotService(FoodSlotRepository slotRepository, NgoRepository ngoRepository, MongoTemplate mongoTemplate) {
        this.slotRepository = slotRepository;
        this.ngoRepository = ngoRepository;
        this.mongoTemplate = mongoTemplate;
    }

    // -- Reading ---------------------------------------------------------------

    /** Public listing. ngoId and status are optional filters; the range defaults to the next two weeks. */
    public List<FoodSlot> list(LocalDate from, LocalDate to, String ngoId, FoodSlotStatus status) {
        LocalDate start = from != null ? from : today();
        LocalDate end = to != null ? to : start.plusDays(DEFAULT_DAYS_SHOWN);
        if (end.isBefore(start))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'to' must not be before 'from'");
        if (start.plusDays(90).isBefore(end))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ask for at most 90 days at a time");

        Criteria criteria = Criteria.where("date").gte(start).lte(end);
        if (ngoId != null && !ngoId.isBlank()) criteria = criteria.and("ngoId").is(ngoId);
        if (status != null) criteria = criteria.and("status").is(status.name());

        List<FoodSlot> slots = mongoTemplate.find(Query.query(criteria), FoodSlot.class);
        // Sorted here, not in the query: Mongo holds mealType as a string, and alphabetical order
        // would put DINNER before LUNCH.
        slots.sort(Comparator.comparing(FoodSlot::getDate)
                .thenComparing(s -> s.getMealType().ordinal())
                .thenComparing(s -> s.getNgoName() == null ? "" : s.getNgoName()));
        return slots;
    }

    // -- NGO actions -----------------------------------------------------------

    public FoodSlot create(String ngoId, String email, FoodSlotRequest req) {
        Ngo ngo = assertOwner(ngoId, email);

        if (req.getDate() == null || req.getMealType() == null || req.getBeneficiaryGroup() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date, mealType and beneficiaryGroup are required");
        if (req.getDate().isBefore(today()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A food slot cannot be in the past");
        if (req.getDate().isAfter(today().plusDays(MAX_DAYS_AHEAD)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Food slots can be posted up to " + MAX_DAYS_AHEAD + " days ahead");

        // Headcount: what the NGO typed for this slot, else the number it saved for this group.
        Integer people = req.getPeopleCount() != null ? req.getPeopleCount() : savedCount(ngo, req.getBeneficiaryGroup());
        if (people == null || people < 1 || people > MAX_PEOPLE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Number of people must be between 1 and " + MAX_PEOPLE + " — enter it, or save it under Food pricing");

        FoodSlotStatus status = req.getStatus() == null ? FoodSlotStatus.OPEN : req.getStatus();

        // Price: when the NGO has set per-person meal costs, the server computes the total and any
        // amount the browser sent is ignored, so every slot is priced the same transparent way.
        // Without saved costs it falls back to the total the NGO typed for this one slot.
        BigDecimal perPerson = costPerPerson(ngo, req.getMealType());
        BigDecimal amount;
        if (perPerson != null) {
            amount = perPerson.multiply(BigDecimal.valueOf(people));
        } else {
            amount = req.getAmount();
            perPerson = amount == null ? null : amount.divide(BigDecimal.valueOf(people), 2, RoundingMode.HALF_UP);
        }

        // An open slot is a price tag a donor will pay, so it must have a valid one. A filled slot
        // is informational ("already covered by X") and the cost is optional.
        if (status == FoodSlotStatus.OPEN || amount != null) validateAmount(amount);

        assertNoWholeDayOverlap(ngoId, req.getDate(), req.getMealType(), req.getBeneficiaryGroup());

        FoodSlot slot = new FoodSlot();
        slot.setNgoId(ngoId);
        slot.setNgoName(ngo.getNgoName());
        slot.setNgoLocation(ngo.getLocation());
        slot.setDate(req.getDate());
        slot.setMealType(req.getMealType());
        slot.setBeneficiaryGroup(req.getBeneficiaryGroup());
        slot.setPeopleCount(people);
        slot.setAmount(amount);
        slot.setCostPerPerson(perPerson);
        slot.setMenu(trim(req.getMenu()));
        slot.setNote(trim(req.getNote()));
        slot.setStatus(status);
        slot.setCreatedAt(Instant.now());
        if (status == FoodSlotStatus.FILLED) {
            slot.setFilledVia("OFFLINE");
            slot.setSponsorName(trim(req.getSponsorName()));
            slot.setFilledAt(Instant.now());
        }

        try {
            return slotRepository.save(slot);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already posted a " + mealName(req.getMealType()) + " slot for this group on " + req.getDate());
        }
    }

    /**
     * Save the NGO's per-person meal costs and headcounts. Replaces both maps wholesale, so an
     * entry left out (or sent as null/0) is removed. Already-posted slots keep the price they had.
     */
    public Ngo updateFoodSettings(String ngoId, String email, FoodSettingsRequest req) {
        Ngo ngo = assertOwner(ngoId, email);

        Map<MealType, BigDecimal> costs = new EnumMap<>(MealType.class);
        if (req.getMealCostPerPerson() != null) {
            req.getMealCostPerPerson().forEach((meal, cost) -> {
                if (meal == null || cost == null || cost.signum() == 0) return;
                if (meal.isFullDay())
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Set breakfast, lunch and dinner separately — a whole day is their total");
                if (cost.signum() < 0 || cost.compareTo(MAX_COST_PER_PERSON) > 0)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "A meal must cost between Rs.1 and Rs." + MAX_COST_PER_PERSON.toPlainString() + " per person");
                if (cost.stripTrailingZeros().scale() > 2)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Costs can have at most 2 decimal places");
                costs.put(meal, cost);
            });
        }

        Map<BeneficiaryGroup, Integer> counts = new EnumMap<>(BeneficiaryGroup.class);
        if (req.getBeneficiaryCounts() != null) {
            req.getBeneficiaryCounts().forEach((group, count) -> {
                if (group == null || count == null || count == 0) return;
                if (count < 0 || count > MAX_PEOPLE)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Number of people must be between 1 and " + MAX_PEOPLE);
                counts.put(group, count);
            });
        }

        ngo.setMealCostPerPerson(costs);
        ngo.setBeneficiaryCounts(counts);
        return ngoRepository.save(ngo);
    }

    /** The NGO found a sponsor outside the platform (a family, a local shop) and marks the meal covered. */
    public FoodSlot markFilledOffline(String ngoId, String slotId, String email, String sponsorName) {
        assertOwner(ngoId, email);
        FoodSlot updated = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(slotId).and("ngoId").is(ngoId)
                        .and("status").is(FoodSlotStatus.OPEN.name())),
                new Update().set("status", FoodSlotStatus.FILLED.name())
                        .set("filledVia", "OFFLINE")
                        .set("sponsorName", trim(sponsorName))
                        .set("filledAt", Instant.now())
                        .unset("heldByUserId").unset("heldUntil"),
                FindAndModifyOptions.options().returnNew(true),
                FoodSlot.class);
        if (updated == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This slot is not open any more");
        return updated;
    }

    /**
     * Undo an offline "filled" — the arrangement fell through. A slot a donor actually PAID for can
     * never be reopened: that would invite a second donor to pay for a meal already paid for.
     */
    public FoodSlot reopen(String ngoId, String slotId, String email) {
        assertOwner(ngoId, email);
        FoodSlot slot = slotRepository.findById(slotId)
                .filter(s -> s.getNgoId().equals(ngoId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Food slot not found"));
        // Posted as filled without a cost: it cannot be offered to donors until it has a price.
        if (slot.getAmount() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This slot has no cost set, so it cannot be opened to donors. Delete it and post a new open slot instead.");

        FoodSlot updated = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(slotId).and("ngoId").is(ngoId)
                        .and("status").is(FoodSlotStatus.FILLED.name()).and("filledVia").is("OFFLINE")),
                new Update().set("status", FoodSlotStatus.OPEN.name())
                        .unset("filledVia").unset("sponsorName").unset("filledAt"),
                FindAndModifyOptions.options().returnNew(true),
                FoodSlot.class);
        if (updated == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a slot you marked filled yourself can be reopened");
        return updated;
    }

    /** Delete a slot posted by mistake. A donor-paid slot stays, as the record of what the money bought. */
    public void delete(String ngoId, String slotId, String email) {
        assertOwner(ngoId, email);
        FoodSlot slot = slotRepository.findById(slotId)
                .filter(s -> s.getNgoId().equals(ngoId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Food slot not found"));
        if ("DONATION".equals(slot.getFilledVia()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A slot a donor has paid for cannot be deleted");
        if (slot.getHeldUntil() != null && slot.getHeldUntil().isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A donor is paying for this slot right now — try again in a few minutes");
        slotRepository.delete(slot);
    }

    // -- Used by DonationService -----------------------------------------------

    /**
     * Called before any money moves. Confirms the slot can still be sponsored and reserves it for
     * this donor for CHECKOUT_HOLD, so a second donor cannot open checkout for the same meal.
     * The same donor re-trying (closed the popup, clicked Pay again) simply renews their own hold.
     *
     * Returns the slot so the caller takes the NGO and the amount from the server-side record,
     * never from what the browser sent.
     */
    public FoodSlot holdForCheckout(String slotId, String donorUserId) {
        Instant now = Instant.now();
        Criteria notHeldByOthers = new Criteria().orOperator(
                Criteria.where("heldUntil").exists(false),
                Criteria.where("heldUntil").is(null),
                Criteria.where("heldUntil").lt(now),
                Criteria.where("heldByUserId").is(donorUserId));

        FoodSlot held = mongoTemplate.findAndModify(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(slotId),
                        Criteria.where("status").is(FoodSlotStatus.OPEN.name()),
                        Criteria.where("date").gte(today()),
                        notHeldByOthers)),
                new Update().set("heldByUserId", donorUserId).set("heldUntil", now.plus(CHECKOUT_HOLD)),
                FindAndModifyOptions.options().returnNew(true),
                FoodSlot.class);

        if (held != null) {
            if (held.getAmount() == null)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This slot has no cost set yet");
            return held;
        }

        // Work out which reason to give — a donor deserves better than a bare "no".
        FoodSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Food slot not found"));
        if (slot.getStatus() == FoodSlotStatus.FILLED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Someone has already sponsored this meal — thank you anyway!");
        if (slot.getDate().isBefore(today()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This meal's date has passed");
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Another donor is paying for this meal right now. Please pick another slot or try again in a few minutes.");
    }

    /**
     * Mark the slot FILLED once its donation is PAID. Conditional on still being OPEN, so this is
     * safe to call from both the browser verify and the Razorpay webhook for the same payment.
     *
     * Returns false if the slot was already filled — possible only if a donor finished paying
     * after their hold expired and someone else sponsored it meanwhile. The money is not lost:
     * it is a normal paid donation to the same NGO, and the NGO spends it on another meal.
     */
    public boolean fillFromDonation(String slotId, String donationId, String donorUserId, String donorFullName) {
        FoodSlot filled = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(slotId).and("status").is(FoodSlotStatus.OPEN.name())),
                new Update().set("status", FoodSlotStatus.FILLED.name())
                        .set("filledVia", "DONATION")
                        .set("donationId", donationId)
                        .set("sponsorUserId", donorUserId)
                        .set("sponsorName", publicName(donorFullName))
                        .set("filledAt", Instant.now())
                        .unset("heldByUserId").unset("heldUntil"),
                FindAndModifyOptions.options().returnNew(true),
                FoodSlot.class);
        return filled != null;
    }

    /** Give the slot back when checkout could not be started, so it does not sit blocked for 15 minutes. */
    public void releaseHold(String slotId, String donorUserId) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(slotId).and("heldByUserId").is(donorUserId)),
                new Update().unset("heldByUserId").unset("heldUntil"),
                FoodSlot.class);
    }

    // -- Helpers ---------------------------------------------------------------

    static LocalDate today() { return LocalDate.now(ZONE); }

    /**
     * "Priya Sharma" -> "Priya S." The slot page is public, and a donor's full name next to what
     * they gave is more than they agreed to publish by paying.
     */
    static String publicName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "A kind donor";
        String[] parts = fullName.trim().split("\\s+");
        return parts.length == 1 ? parts[0] : parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }

    /**
     * One person's cost for this meal from the NGO's saved prices, or null if not set. A whole day
     * is breakfast + lunch + dinner, so it needs all three.
     */
    static BigDecimal costPerPerson(Ngo ngo, MealType meal) {
        Map<MealType, BigDecimal> costs = ngo.getMealCostPerPerson();
        if (costs == null) return null;
        if (!meal.isFullDay()) return costs.get(meal);
        BigDecimal total = BigDecimal.ZERO;
        for (MealType single : MealType.SINGLE_MEALS) {
            BigDecimal cost = costs.get(single);
            if (cost == null) return null;
            total = total.add(cost);
        }
        return total;
    }

    private static Integer savedCount(Ngo ngo, BeneficiaryGroup group) {
        return ngo.getBeneficiaryCounts() == null ? null : ngo.getBeneficiaryCounts().get(group);
    }

    /**
     * A whole-day slot and a separate lunch slot for the same group and day would be the same meal
     * on sale twice, so two donors could pay for one lunch. The unique index only catches an exact
     * duplicate; this catches the overlap. (Two NGO tabs posting both at the same instant could
     * still slip past — an NGO racing itself is not worth a transaction here.)
     */
    private void assertNoWholeDayOverlap(String ngoId, LocalDate date, MealType meal, BeneficiaryGroup group) {
        Criteria sameDay = Criteria.where("ngoId").is(ngoId).and("date").is(date)
                .and("beneficiaryGroup").is(group.name());
        Criteria clash = meal.isFullDay()
                ? sameDay.and("mealType").ne(MealType.FULL_DAY.name())
                : sameDay.and("mealType").is(MealType.FULL_DAY.name());
        if (mongoTemplate.exists(Query.query(clash), FoodSlot.class))
            throw new ResponseStatusException(HttpStatus.CONFLICT, meal.isFullDay()
                    ? "You already posted separate meals for this group on " + date + ". Delete them first, or keep posting single meals."
                    : "A whole-day slot already covers this group on " + date + ".");
    }

    private static String mealName(MealType meal) {
        return meal.isFullDay() ? "whole-day" : meal.name().toLowerCase();
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An open slot needs a price — save your per-person meal costs under Food pricing, or enter the cost of this meal");
        if (amount.compareTo(MAX_SLOT_AMOUNT) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum slot cost is Rs.10,00,000");
        if (amount.stripTrailingZeros().scale() > 2)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount can have at most 2 decimal places");
    }

    private static String trim(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private Ngo assertOwner(String ngoId, String email) {
        Ngo ngo = ngoRepository.findById(ngoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NGO not found"));
        if (!ngo.getEmail().equalsIgnoreCase(email))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage your own NGO");
        return ngo;
    }
}
