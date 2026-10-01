package app.dto;

import app.enums.BeneficiaryGroup;
import app.enums.FoodSlotStatus;
import app.enums.MealType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What an NGO sends to post a food slot. status may be OPEN (needs a sponsor) or FILLED (already
 * covered — sponsorName then says by whom, e.g. "Sharma family"). Everything else about the slot
 * (NGO name, timestamps, who paid) is decided server-side.
 */
public class FoodSlotRequest {
    private LocalDate date;
    private MealType mealType;
    private BeneficiaryGroup beneficiaryGroup;
    private Integer peopleCount;
    private BigDecimal amount;
    private String menu, note, sponsorName;
    private FoodSlotStatus status;

    public LocalDate getDate() { return date; } public void setDate(LocalDate v) { this.date = v; }
    public MealType getMealType() { return mealType; } public void setMealType(MealType v) { this.mealType = v; }
    public BeneficiaryGroup getBeneficiaryGroup() { return beneficiaryGroup; } public void setBeneficiaryGroup(BeneficiaryGroup v) { this.beneficiaryGroup = v; }
    public Integer getPeopleCount() { return peopleCount; } public void setPeopleCount(Integer v) { this.peopleCount = v; }
    public BigDecimal getAmount() { return amount; } public void setAmount(BigDecimal v) { this.amount = v; }
    public String getMenu() { return menu; } public void setMenu(String v) { this.menu = v; }
    public String getNote() { return note; } public void setNote(String v) { this.note = v; }
    public String getSponsorName() { return sponsorName; } public void setSponsorName(String v) { this.sponsorName = v; }
    public FoodSlotStatus getStatus() { return status; } public void setStatus(FoodSlotStatus v) { this.status = v; }
}
