import { Sunrise, Sun, Moon, UtensilsCrossed } from 'lucide-react';

const ICONS = { FULL_DAY: UtensilsCrossed, BREAKFAST: Sunrise, LUNCH: Sun, DINNER: Moon };

/** One consistent icon per meal, so a donor and an NGO read the same symbol for "lunch". */
export default function MealIcon({ meal, size = 16, ...rest }) {
  const Icon = ICONS[meal] || UtensilsCrossed;
  return <Icon size={size} strokeWidth={2} aria-hidden="true" {...rest} />;
}
