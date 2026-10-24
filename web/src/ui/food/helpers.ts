// Small pure helpers for the Food tab (Android FoodScreen.kt FoodRow stepper rules).

/** Whole servings, with a half serving as the smallest step; 0 means "untick". */
export const qtyMinus = (q: number): number => (q > 1 ? q - 1 : q === 1 ? 0.5 : 0);
export const qtyPlus = (q: number): number => (q < 1 ? 1 : Math.min(20, q + 1));
/** "2×" or "0.5×". */
export const fmtQty = (q: number): string => `${Number.isInteger(q) ? q : Math.round(q * 100) / 100}×`;

/** The 7 days ending today, oldest first. */
export const lastSevenDays = (today: number): number[] => Array.from({ length: 7 }, (_, i) => today - 6 + i);
