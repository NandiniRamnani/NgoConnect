package app.model;

import app.enums.BeneficiaryGroup;
import app.enums.FoodSlotStatus;
import app.enums.MealType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One meal, on one day, for one group of people an NGO looks after — e.g. "Lunch for 40 orphan
 * children on 29 Sep, costs Rs.2,400". The NGO posts it as OPEN when nobody is providing that meal
 * yet, and any donor can sponsor it by paying the cost; or the NGO posts / marks it FILLED when the
 * meal is already covered, so donors can see which days still need help.
 *
 * The unique index stops an NGO accidentally posting the same meal twice for the same group on the
 * same day — two OPEN slots for one lunch would let two donors pay for a single meal.
 */
@Document(collection = "food_slots")
@CompoundIndex(name = "one_slot_per_meal", unique = true,
        def = "{'ngoId': 1, 'date': 1, 'mealType': 1, 'beneficiaryGroup': 1}")
public class FoodSlot {
    @Id private String id;
    private String ngoId, ngoName, ngoLocation;
    private LocalDate date;
    private MealType mealType;
    private BeneficiaryGroup beneficiaryGroup;
    private Integer peopleCount;
    /** What it costs to feed everyone for this meal. The donor pays exactly this — see DonationService. */
    private BigDecimal amount;
    /**
     * What one person's share of this meal costs, as the NGO priced it. amount = costPerPerson x
     * peopleCount, so a donor sees "Rs.60 x 40 people" rather than an unexplained total. Copied
     * onto the slot so a later change to the NGO's prices does not rewrite what was already posted.
     */
    private BigDecimal costPerPerson;
    private String menu, note;
    private FoodSlotStatus status;

    /** "DONATION" when a donor paid through NGOConnect, "OFFLINE" when the NGO marked it filled itself. */
    private String filledVia;
    /** Public display name only — first name and initial, never the full name or email. */
    private String sponsorName;
    @JsonIgnore private String sponsorUserId;
    @JsonIgnore private String donationId;

    /**
     * A short reservation taken when a donor opens checkout, so two donors cannot both be paying
     * for the same meal at the same time. Not exposed: the public only needs "open" or "filled".
     */
    @JsonIgnore private String heldByUserId;
    @JsonIgnore private Instant heldUntil;

    private Instant createdAt, filledAt;

    public String getId() { return id; } public void setId(String v) { this.id = v; }
    public String getNgoId() { return ngoId; } public void setNgoId(String v) { this.ngoId = v; }
    public String getNgoName() { return ngoName; } public void setNgoName(String v) { this.ngoName = v; }
    public String getNgoLocation() { return ngoLocation; } public void setNgoLocation(String v) { this.ngoLocation = v; }
    public LocalDate getDate() { return date; } public void setDate(LocalDate v) { this.date = v; }
    public MealType getMealType() { return mealType; } public void setMealType(MealType v) { this.mealType = v; }
    public BeneficiaryGroup getBeneficiaryGroup() { return beneficiaryGroup; } public void setBeneficiaryGroup(BeneficiaryGroup v) { this.beneficiaryGroup = v; }
    public Integer getPeopleCount() { return peopleCount; } public void setPeopleCount(Integer v) { this.peopleCount = v; }
    public BigDecimal getAmount() { return amount; } public void setAmount(BigDecimal v) { this.amount = v; }
    public BigDecimal getCostPerPerson() { return costPerPerson; } public void setCostPerPerson(BigDecimal v) { this.costPerPerson = v; }
    public String getMenu() { return menu; } public void setMenu(String v) { this.menu = v; }
    public String getNote() { return note; } public void setNote(String v) { this.note = v; }
    public FoodSlotStatus getStatus() { return status; } public void setStatus(FoodSlotStatus v) { this.status = v; }
    public String getFilledVia() { return filledVia; } public void setFilledVia(String v) { this.filledVia = v; }
    public String getSponsorName() { return sponsorName; } public void setSponsorName(String v) { this.sponsorName = v; }
    public String getSponsorUserId() { return sponsorUserId; } public void setSponsorUserId(String v) { this.sponsorUserId = v; }
    public String getDonationId() { return donationId; } public void setDonationId(String v) { this.donationId = v; }
    public String getHeldByUserId() { return heldByUserId; } public void setHeldByUserId(String v) { this.heldByUserId = v; }
    public Instant getHeldUntil() { return heldUntil; } public void setHeldUntil(Instant v) { this.heldUntil = v; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getFilledAt() { return filledAt; } public void setFilledAt(Instant v) { this.filledAt = v; }
}
