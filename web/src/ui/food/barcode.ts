export type BarcodeProduct = {
  code: string;
  name: string;
  brand: string;
  kcal: number;
  protein: number;
  carbs: number;
  serving: string;
};

const num = (v: unknown): number | null => {
  const n = typeof v === 'string' ? Number(v) : v;
  return typeof n === 'number' && Number.isFinite(n) && n >= 0 ? n : null;
};

/** Parses an Open Food Facts v2 product JSON payload into a BarcodeProduct, or null if not found / missing calories. */
export function parseOpenFoodFacts(code: string, json: unknown): BarcodeProduct | null {
  if (typeof json !== 'object' || json === null) return null;
  const root = json as Record<string, unknown>;
  if (root.status !== 1 || typeof root.product !== 'object' || root.product === null) return null;
  const p = root.product as Record<string, unknown>;
  const nut = (typeof p.nutriments === 'object' && p.nutriments !== null ? p.nutriments : {}) as Record<string, unknown>;
  const kcal =
    num(nut['energy-kcal_serving']) ??
    num(nut['energy-kcal_100g']) ??
    num(nut['energy-kcal']);
  if (kcal == null) return null;
  const useServing = num(nut['energy-kcal_serving']) != null;
  const protein = (useServing ? num(nut.proteins_serving) : num(nut.proteins_100g)) ?? num(nut.proteins) ?? 0;
  const carbs = (useServing ? num(nut.carbohydrates_serving) : num(nut.carbohydrates_100g)) ?? num(nut.carbohydrates) ?? 0;
  const name = (typeof p.product_name === 'string' && p.product_name.trim()) || '';
  const brand = (typeof p.brands === 'string' && p.brands.split(',')[0].trim()) || '';
  const serving = (typeof p.serving_size === 'string' && p.serving_size.trim()) || (useServing ? '' : '100 g');
  return {
    code,
    name,
    brand,
    kcal: Math.round(kcal),
    protein: Math.round(protein * 10) / 10,
    carbs: Math.round(carbs * 10) / 10,
    serving,
  };
}

export async function lookupBarcode(code: string, signal?: AbortSignal): Promise<BarcodeProduct | null> {
  const clean = code.trim();
  if (!clean) return null;
  const url = `https://world.openfoodfacts.org/api/v2/product/${encodeURIComponent(clean)}.json?fields=product_name,brands,nutriments,serving_size`;
  const res = await fetch(url, { signal });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return parseOpenFoodFacts(clean, await res.json());
}
