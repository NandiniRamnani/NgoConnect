package app.enums;

/**
 * OPEN   - nobody is providing this meal yet; any donor can sponsor it.
 * FILLED - the meal is covered, either by a donor paying through NGOConnect or by someone the NGO
 *          arranged offline (see FoodSlot.filledVia for which).
 */
public enum FoodSlotStatus {
    OPEN, FILLED
}
