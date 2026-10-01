package app.enums;

/**
 * Which meal of the day a food slot covers. Declared in serving order, so ordinal() sorts a day's
 * slots the way people read them — breakfast before lunch before dinner. Mongo stores the name,
 * and sorting by name would put DINNER before LUNCH, so the service sorts by this order instead.
 *
 * FULL_DAY is one slot covering all three meals, for a donor who wants to feed everyone for the
 * whole day. It comes first so it heads the day it covers. It cannot sit alongside separate
 * breakfast/lunch/dinner slots for the same group and day — see FoodSlotService.create.
 */
public enum MealType {
    FULL_DAY, BREAKFAST, LUNCH, DINNER;

    /** The meals a whole day is made of — the ones an NGO sets a per-person cost for. */
    public static final MealType[] SINGLE_MEALS = { BREAKFAST, LUNCH, DINNER };

    public boolean isFullDay() { return this == FULL_DAY; }
}
