// Pure helpers for the photo scan (port of FoodRepository.kt scan/scanInstructions/parseScan) plus the
// browser-only photo shrink. The photo is never stored: it lives in memory until the screen closes.
import { AiError, geminiGenerate, imagePart, scanModelsFor, textPart } from '../../data/ai';
import { loadSettings } from '../../data/settings';

export type ScanItem = { name: string; grams: number; kcal: number; protein: number; carbs: number };
export type FoodScan = { dish: string; items: ScanItem[]; handVisible: boolean; note: string };

export const MAX_EDGE = 1024;
export const JPEG_QUALITY = 0.8;

/** Size that fits inside max x max, keeping the ratio. Never enlarges, never below 1 px. */
export function fitWithin(width: number, height: number, max = MAX_EDGE): { width: number; height: number } {
  if (!(width > 0) || !(height > 0)) return { width: 1, height: 1 };
  const scale = Math.min(1, max / Math.max(width, height));
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) };
}

async function decode(file: Blob): Promise<{ source: CanvasImageSource; width: number; height: number; close: () => void }> {
  if (typeof createImageBitmap === 'function') {
    try {
      const bmp = await createImageBitmap(file, { imageOrientation: 'from-image' });
      return { source: bmp, width: bmp.width, height: bmp.height, close: () => bmp.close() };
    } catch { /* fall through to <img> */ }
  }
  const url = URL.createObjectURL(file);
  const img = new Image();
  img.src = url;
  try { await img.decode(); } catch { URL.revokeObjectURL(url); throw new Error('decode'); }
  return { source: img, width: img.naturalWidth, height: img.naturalHeight, close: () => URL.revokeObjectURL(url) };
}

/** Shrinks a picked photo to at most 1024 px on its long edge and re-encodes it as JPEG. */
export async function shrinkPhoto(file: Blob): Promise<{ blob: Blob; base64: string }> {
  const img = await decode(file);
  try {
    const { width, height } = fitWithin(img.width, img.height);
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('canvas');
    ctx.drawImage(img.source, 0, 0, width, height);
    const blob = await new Promise<Blob>((res, rej) => canvas.toBlob((b) => (b ? res(b) : rej(new Error('encode'))), 'image/jpeg', JPEG_QUALITY));
    const data = canvas.toDataURL('image/jpeg', JPEG_QUALITY);
    canvas.width = canvas.height = 0;
    return { blob, base64: data.slice(data.indexOf(',') + 1) };
  } finally { img.close(); }
}

export function scanInstructions(handLengthCm: number, palmWidthCm: number): string {
  const handHint = handLengthCm > 0
    ? `If the user's hand is visible, use it as an exact spatial ruler: hand length = ${handLengthCm} cm, palm width = ${palmWidthCm} cm.`
    : 'If a hand, standard fork (~19 cm), spoon, mug, or dinner plate (~26 cm rim) is visible, use it as a geometric scale reference.';
  return `You are an expert sports nutritionist estimating macros from meal photos for a fitness app. Be realistic, never optimistic.
1. ${handHint}
2. Identify each distinct food component separately, including visible cooking oil, ghee, butter, sauces, or dressings.
3. Recognize Saudi / Middle Eastern and home-cooked items accurately (e.g., Kabsa, Mandi, Saleeg, Mutabbaq, Shawarma, Paratha flatbread ~260-300 kcal per 80g piece, eggs, cheese, laban, dates, cappuccino/mocha) as well as standard gym foods.
4. Anchor your math on standard cooked nutritional densities per 100g (e.g. cooked chicken breast ~165 kcal & 31g protein; cooked rice ~130 kcal & 2.7g protein & 28g carbs; cooked lean beef ~250 kcal & 26g protein; whole egg ~72 kcal & 6.3g protein; olive oil/ghee ~900 kcal per 100g).
5. Before emitting each item, verify internally that kcal roughly equals (protein * 4 + carbs * 4 + estimated_fat * 9).
6. Use short, plain food names (e.g. "Grilled chicken breast", "Basmati rice", "Paratha flatbread").
7. If the photo shows no food or drink, return an empty items list.
Reply ONLY with JSON: {"dish": string, "items": [{"name": string, "grams": int, "kcal": int, "protein": number, "carbs": number}], "handVisible": boolean, "note": string}
"note" is one short sentence about portion/cooking assumptions or anything uncertain, or empty.`;
}

/** First "{" to last "}" (the model may wrap the JSON in a code block). */
export function extractJson(raw: string): string {
  const start = raw.indexOf('{');
  const end = raw.lastIndexOf('}');
  return start >= 0 && end > start ? raw.slice(start, end + 1) : raw;
}

const num = (v: unknown): number => {
  const n = typeof v === 'string' ? Number(v) : v;
  return typeof n === 'number' && Number.isFinite(n) ? n : 0;
};

/** Reads the Gemini food JSON. Null when it isn't JSON of the expected shape; items without a name are dropped. */
export function parseScan(text: string): FoodScan | null {
  let root: unknown;
  try { root = JSON.parse(extractJson(text)); } catch { return null; }
  if (typeof root !== 'object' || root === null || Array.isArray(root)) return null;
  const o = root as Record<string, unknown>;
  if (o.items !== undefined && !Array.isArray(o.items)) return null;
  const items: ScanItem[] = [];
  for (const raw of (o.items as unknown[] | undefined) ?? []) {
    if (typeof raw !== 'object' || raw === null) continue;
    const r = raw as Record<string, unknown>;
    const name = typeof r.name === 'string' ? r.name.trim() : '';
    if (!name) continue;
    items.push({ name, grams: Math.max(0, Math.round(num(r.grams))), kcal: Math.max(0, Math.round(num(r.kcal))), protein: Math.max(0, num(r.protein)), carbs: Math.max(0, num(r.carbs)) });
  }
  return {
    dish: typeof o.dish === 'string' && o.dish.trim() ? o.dish.trim() : 'Meal',
    items, handVisible: o.handVisible === true, note: typeof o.note === 'string' ? o.note.trim() : '',
  };
}

/** Sends the shrunk photo to Gemini with the Android scan prompt. Throws AiError. */
export async function scanMeal(base64Jpeg: string, signal?: AbortSignal): Promise<FoodScan> {
  const s = await loadSettings();
  if (!s.apiKey.trim()) throw new AiError('no_key');
  const raw = await geminiGenerate({
    system: scanInstructions(s.handLengthCm, s.palmWidthCm),
    parts: [textPart('Estimate this meal with realistic cooked weights and macro math.'), imagePart(base64Jpeg)],
    json: true, lowThinking: true, models: scanModelsFor(s), signal,
  });
  const scan = parseScan(raw);
  if (!scan) throw new AiError('unreadable', raw.slice(0, 200));
  if (scan.items.length === 0) throw new AiError('no_food');
  return scan;
}

export const PORTIONS = [{ id: 'small', factor: 0.75 }, { id: 'normal', factor: 1 }, { id: 'large', factor: 1.25 }] as const;
export const COOKING_FATS = [{ id: 'light', kcal: 0 }, { id: 'normal', kcal: 50 }, { id: 'heavy', kcal: 150 }] as const;

/** Scales an item to a corrected weight so calories and macros stay consistent. */
export function withGrams(item: ScanItem, grams: number): ScanItem {
  const f = item.grams > 0 ? grams / item.grams : 1;
  return { ...item, grams: Math.round(grams), kcal: Math.round(item.kcal * f), protein: item.protein * f, carbs: item.carbs * f };
}

/** Totals for the ticked items, portion factor and extra fat applied (Android ResultSheet). */
export function scanTotals(items: readonly ScanItem[], portion: number, fatKcal: number): { kcal: number; protein: number; carbs: number } {
  return {
    kcal: Math.round(items.reduce((a, i) => a + i.kcal, 0) * portion) + (items.length ? fatKcal : 0),
    protein: items.reduce((a, i) => a + i.protein, 0) * portion,
    carbs: items.reduce((a, i) => a + i.carbs, 0) * portion,
  };
}
