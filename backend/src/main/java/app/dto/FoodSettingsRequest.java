package app.dto;

import app.enums.BeneficiaryGroup;
import app.enums.MealType;
import java.math.BigDecimal;
import java.util.Map;

/**
 * An NGO's food pricing: per-person cost of each meal, and how many people it feeds in each group.
 * Example: { "mealCostPerPerson": { "BREAKFAST": 30, "LUNCH": 60, "DINNER": 60 },
 *            "beneficiaryCounts": { "ORPHAN_CHILDREN": 40 } }
 * Omitted or null entries are removed, so the NGO can clear a group it no longer looks after.
 */
public class FoodSettingsRequest {
    private Map<MealType, BigDecimal> mealCostPerPerson;
    private Map<BeneficiaryGroup, Integer> beneficiaryCounts;

    public Map<MealType, BigDecimal> getMealCostPerPerson() { return mealCostPerPerson; } public void setMealCostPerPerson(Map<MealType, BigDecimal> v) { this.mealCostPerPerson = v; }
    public Map<BeneficiaryGroup, Integer> getBeneficiaryCounts() { return beneficiaryCounts; } public void setBeneficiaryCounts(Map<BeneficiaryGroup, Integer> v) { this.beneficiaryCounts = v; }
}
