/** The single meals an NGO prices per person. */
export const MEAL_TYPES = ['BREAKFAST', 'LUNCH', 'DINNER'];
/** Everything an NGO can post: a single meal, or the whole day (all three) as one slot. */
export const SLOT_TYPES = ['FULL_DAY', ...MEAL_TYPES];
export const MEAL_LABEL = { FULL_DAY: 'Whole day (all meals)', BREAKFAST: 'Breakfast', LUNCH: 'Lunch', DINNER: 'Dinner' };
export const MEAL_ICON = { FULL_DAY: '🍽️', BREAKFAST: '🌅', LUNCH: '☀️', DINNER: '🌙' };

/** Sort order within a day, matching the backend: whole day first, then serving order. */
export const slotOrder = (mealType) => SLOT_TYPES.indexOf(mealType);

/**
 * One person's cost for a slot type from an NGO's saved prices, or null if not set. Mirrors
 * FoodSlotService.costPerPerson — the backend does the real calculation; this is only the preview.
 */
export function costPerPerson(ngo, mealType) {
  const costs = ngo?.mealCostPerPerson || {};
  if (mealType !== 'FULL_DAY') return costs[mealType] != null ? Number(costs[mealType]) : null;
  if (MEAL_TYPES.some(m => costs[m] == null)) return null;
  return MEAL_TYPES.reduce((sum, m) => sum + Number(costs[m]), 0);
}

export const GROUPS = ['ORPHAN_CHILDREN', 'ELDERLY', 'BLIND', 'DIFFERENTLY_ABLED', 'OTHER'];
export const GROUP_LABEL = {
  ORPHAN_CHILDREN: 'Orphan children',
  ELDERLY: 'Elderly',
  BLIND: 'Blind people',
  DIFFERENTLY_ABLED: 'Differently-abled',
  OTHER: 'Others',
};

/**
 * The backend sends dates as plain "YYYY-MM-DD" calendar days. `new Date("2026-09-29")` would read
 * that as midnight UTC, which is the previous evening anywhere west of Greenwich — so parse the
 * parts and build a LOCAL date instead.
 */
export function parseDay(iso) {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

/** Local date -> "YYYY-MM-DD", the format the API and <input type="date"> both use. */
export function toIsoDay(date) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function addDays(date, days) {
  const copy = new Date(date);
  copy.setDate(copy.getDate() + days);
  return copy;
}

/** "Today", "Tomorrow", or "Wed, 1 Oct". */
export function dayLabel(iso) {
  const today = toIsoDay(new Date());
  if (iso === today) return 'Today';
  if (iso === toIsoDay(addDays(new Date(), 1))) return 'Tomorrow';
  return parseDay(iso).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' });
}

/** `count` consecutive local days as "YYYY-MM-DD", starting `offset` days from today. */
export function nextDays(count, offset = 0) {
  const start = addDays(new Date(), offset);
  return Array.from({ length: count }, (_, i) => toIsoDay(addDays(start, i)));
}

/** The pieces a calendar tile needs: { weekday: "Wed", day: 1, month: "Oct", isToday }. */
export function dayParts(iso) {
  const d = parseDay(iso);
  return {
    weekday: d.toLocaleDateString('en-IN', { weekday: 'short' }),
    day: d.getDate(),
    month: d.toLocaleDateString('en-IN', { month: 'short' }),
    isToday: iso === toIsoDay(new Date()),
  };
}

/** "Wednesday, 1 October" — for headings, where there is room to spell the date out. */
export function longDayLabel(iso) {
  return parseDay(iso).toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long' });
}

/** Short group names for tight spaces like the NGO's planner cells. */
export const GROUP_SHORT = {
  ORPHAN_CHILDREN: 'Children',
  ELDERLY: 'Elderly',
  BLIND: 'Blind',
  DIFFERENTLY_ABLED: 'Diff.-abled',
  OTHER: 'Others',
};

/**
 * Would posting (date, meal, group) clash with a slot the NGO already has? Mirrors the backend's
 * unique index plus its whole-day overlap rule, so the form can skip a clash instead of failing on it.
 */
export function slotClash(existing, date, mealType, group) {
  return existing.find(s => s.date === date && s.beneficiaryGroup === group &&
    (s.mealType === mealType || s.mealType === 'FULL_DAY' || mealType === 'FULL_DAY'));
}

/**
 * Where a "Sponsor" button leads. A logged-out donor goes to login first, and login then sends them
 * straight on to checkout for this same meal — without that they landed on their dashboard and had
 * to find the meal all over again.
 */
export function sponsorSlot(navigate, user, slot) {
  const checkout = { pathname: '/donate', state: { foodSlot: slot } };
  if (!user) {
    navigate('/login', { state: { message: 'Log in to sponsor this meal — you\'ll go straight to payment.', redirectTo: checkout } });
    return;
  }
  navigate(checkout.pathname, { state: checkout.state });
}

export const formatRupees = (amount) =>
  amount == null ? '—' : `₹${Number(amount).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;
