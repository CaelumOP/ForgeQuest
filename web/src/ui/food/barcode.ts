import { getLang, type Lang } from '../../i18n';
import type { FoodRow } from '../../data/db';
import { cacheBarcodeProduct, findFoodByBarcode, getCachedBarcodeProduct } from '../../data/food';

export const BARCODE_FORMATS = [
  'ean_13',
  'ean_8',
  'upc_a',
  'upc_e',
  'code_128',
  'code_39',
  'itf',
  'qr_code',
  'data_matrix',
] as const;

export type SupportedBarcodeFormat = (typeof BARCODE_FORMATS)[number];

export type DetectedBarcodeLike = {
  rawValue?: string;
  format?: string;
};

export type BarcodeDetectorLike = {
  detect: (src: CanvasImageSource | HTMLVideoElement | ImageBitmap | Blob) => Promise<DetectedBarcodeLike[]>;
};

export type BarcodeProduct = {
  code: string;
  name: string;
  brand: string;
  kcal: number;
  protein: number;
  carbs: number;
  serving: string;
};

export type BarcodeLookupResult =
  | { status: 'own'; code: string; food: FoodRow }
  | { status: 'cached'; code: string; product: BarcodeProduct }
  | { status: 'online'; code: string; product: BarcodeProduct }
  | { status: 'not_found'; code: string }
  | { status: 'offline'; code: string };

const KJ_PER_KCAL = 4.184;
const OFF_TIMEOUT_MS = 8000;
const OFF_FIELDS =
  'product_name,product_name_ar,brands,nutriments,serving_quantity,quantity,product_quantity,product_quantity_unit,serving_size';

// ---------------------------------------------------------------- Check digits, UPC-E, GS1 & normalisation

/** Standard GS1 Mod-10 check digit validation for 8-, 12-, 13- or 14-digit GTINs (EAN-8, UPC-A, EAN-13, GTIN-14). */
export function isValidGtinCheckDigit(digits: string): boolean {
  if (!/^(?:\d{8}|\d{12}|\d{13}|\d{14})$/.test(digits)) return false;
  let sum = 0;
  for (let i = digits.length - 2, pos = 0; i >= 0; i--, pos++) {
    const d = digits.charCodeAt(i) - 48;
    sum += d * (pos % 2 === 0 ? 3 : 1);
  }
  const expected = (10 - (sum % 10)) % 10;
  return expected === digits.charCodeAt(digits.length - 1) - 48;
}

/** Expands an 8-digit UPC-E (number system 0 or 1) to its 12-digit UPC-A form, mirroring Android Barcodes.expandUpcE. */
export function expandUpcE(e: string): string | null {
  if (!/^[01]\d{7}$/.test(e)) return null;
  const ns = e[0];
  const d = e.slice(1, 7);
  const check = e[7];
  let body: string;
  switch (d[5]) {
    case '0':
    case '1':
    case '2':
      body = `${d[0]}${d[1]}${d[5]}0000${d[2]}${d[3]}${d[4]}`;
      break;
    case '3':
      body = `${d[0]}${d[1]}${d[2]}00000${d[3]}${d[4]}`;
      break;
    case '4':
      body = `${d[0]}${d[1]}${d[2]}${d[3]}00000${d[4]}`;
      break;
    default:
      body = `${d[0]}${d[1]}${d[2]}${d[3]}${d[4]}0000${d[5]}`;
      break;
  }
  return `${ns}${body}${check}`;
}

/** Validates the EAN/UPC/GTIN check digit for a digit string. For UPC-E, checks the expanded 12-digit UPC-A. */
export function isValidBarcodeCheckDigit(raw: string, format?: string): boolean {
  const digits = raw.replace(/\D/g, '');
  if (format === 'upc_e') {
    const expanded = expandUpcE(digits);
    return expanded !== null && isValidGtinCheckDigit(expanded);
  }
  if (format === 'ean_8') return digits.length === 8 && isValidGtinCheckDigit(digits);
  if (format === 'upc_a') return digits.length === 12 && isValidGtinCheckDigit(digits);
  if (format === 'ean_13') return digits.length === 13 && isValidGtinCheckDigit(digits);
  if (digits.length === 8) {
    if (isValidGtinCheckDigit(digits)) return true;
    const expanded = expandUpcE(digits);
    return expanded !== null && isValidGtinCheckDigit(expanded);
  }
  return isValidGtinCheckDigit(digits);
}

/**
 * Extracts a GTIN from GS1 AI (01) payloads found in DataMatrix, GS1-128, or QR codes (including GS1 Digital Link URLs).
 * Returns the canonical retail code (13 digits when GTIN-14 starts with '0', else 14 digits) or null.
 */
export function extractGs1Gtin(raw: string): string | null {
  const trimmed = raw.trim();
  if (!trimmed) return null;

  // GS1 Digital Link URL: https://.../01/<8..14 digits>
  if (/^https?:\/\//i.test(trimmed)) {
    const linkMatch = /\/01\/(\d{8}|\d{12}|\d{13}|\d{14})(?:[/?#]|$)/.exec(trimmed);
    if (!linkMatch) return null;
    const candidate = linkMatch[1];
    return isValidGtinCheckDigit(candidate) ? normalizeGtin14(candidate) : null;
  }

  // Human-readable parenthesized AI: (01)04006381333931...
  const parenMatch = /(?:^|[\u001d)\]])\s*\(01\)\s*(\d{14})/.exec(trimmed);
  if (parenMatch) {
    const gtin = parenMatch[1];
    return isValidGtinCheckDigit(gtin) ? normalizeGtin14(gtin) : null;
  }

  // Raw GS1 element string with optional AIM symbology identifier (]d2, ]Q3, ]C1, ]e0) and/or FNC1 (\u001d)
  const stripped = trimmed.replace(/^\][A-Za-z]\d/, '').replace(/^\u001d+/, '');
  if (stripped.startsWith('01') && stripped.length >= 16) {
    const gtin = stripped.slice(2, 16);
    if (/^\d{14}$/.test(gtin) && isValidGtinCheckDigit(gtin)) {
      return normalizeGtin14(gtin);
    }
  }

  return null;
}

function normalizeGtin14(gtin: string): string {
  if (gtin.length === 14 && gtin.startsWith('0')) return gtin.slice(1);
  return gtin;
}

/** Digits only for standard shop lengths (EAN-8, UPC-A, EAN-13, GTIN-14); null otherwise. */
export function cleanBarcode(raw: string): string | null {
  const digits = raw.replace(/\D/g, '');
  return digits.length === 8 || digits.length === 12 || digits.length === 13 || digits.length === 14 ? digits : null;
}

/**
 * Normalises a scanned barcode (checking GS1 AI (01), EAN/UPC check digits, and rejecting non-GTIN QR payloads).
 * Returns null if the scan should be ignored.
 */
export function normalizeScannedCode(raw: string, format?: string): string | null {
  const trimmed = raw.trim();
  if (!trimmed) return null;

  const gs1 = extractGs1Gtin(trimmed);
  if (gs1) return gs1;

  if (format === 'qr_code' || format === 'data_matrix') {
    const digits = cleanBarcode(trimmed);
    if (!digits || digits !== trimmed || !isValidBarcodeCheckDigit(digits)) return null;
    return normalizeGtin14(digits);
  }

  if (format === 'ean_13' || format === 'ean_8' || format === 'upc_a' || format === 'upc_e') {
    const digits = trimmed.replace(/\D/g, '');
    if (!digits || !isValidBarcodeCheckDigit(digits, format)) return null;
    return digits;
  }

  // code_128, code_39, itf, or manual entry
  const digitsOnly = /^\d+$/.test(trimmed);
  if (digitsOnly) {
    if (trimmed.length === 8 || trimmed.length === 12 || trimmed.length === 13 || trimmed.length === 14) {
      return isValidBarcodeCheckDigit(trimmed) ? normalizeGtin14(trimmed) : null;
    }
    return trimmed.length >= 4 ? trimmed : null;
  }

  // Non-digit Code 128 / Code 39 SKU (ignore control characters / URLs)
  if (format === 'code_128' || format === 'code_39' || format === 'itf') {
    return trimmed.length >= 3 && !/[\x00-\x1f]/.test(trimmed) ? trimmed : null;
  }

  // Manual entry fallback: allow non-URL printable codes >= 3 chars
  return trimmed.length >= 3 && !/^https?:\/\//i.test(trimmed) ? trimmed : null;
}

/**
 * Every written form of the same product, mirroring Android Barcodes.variants:
 * UPC-A <-> EAN-13 with leading 0, UPC-E expanded to UPC-A + EAN-13, GTIN-14 stripped of leading zeros.
 */
export function barcodeVariants(raw: string): string[] {
  const gs1 = extractGs1Gtin(raw);
  const input = gs1 ?? raw.trim();
  if (!input) return [];

  const isDigits = /^\d+$/.test(input);
  const c = isDigits ? input : cleanBarcode(input);
  if (!c) {
    return /^https?:\/\//i.test(input) ? [] : [input];
  }

  const out: string[] = [];
  const push = (v: string | null | undefined) => {
    if (v && !out.includes(v)) out.push(v);
  };

  if (c.length === 8 && (c[0] === '0' || c[0] === '1')) {
    push(c);
    const expanded = expandUpcE(c);
    if (expanded) {
      push(expanded);
      push(`0${expanded}`);
    }
  } else if (c.length === 12) {
    push(c);
    push(`0${c}`);
  } else if (c.length === 13 && c.startsWith('0')) {
    push(c);
    push(c.slice(1));
  } else if (c.length === 14 && c.startsWith('0')) {
    push(c.slice(1));
    if (c.startsWith('00')) push(c.slice(2));
    if (c.startsWith('000000')) push(c.slice(6));
    push(c);
  } else {
    push(c);
  }

  return out;
}

/** Requires the exact same normalized code on `required` consecutive frames to prevent single-frame misreads. */
export function createConsecutiveFilter(required = 2): {
  push: (code: string | null) => string | null;
  reset: () => void;
} {
  let last: string | null = null;
  let count = 0;
  return {
    push(code: string | null): string | null {
      if (!code) {
        last = null;
        count = 0;
        return null;
      }
      if (code === last) {
        count += 1;
      } else {
        last = code;
        count = 1;
      }
      return count >= required ? code : null;
    },
    reset() {
      last = null;
      count = 0;
    },
  };
}

// ---------------------------------------------------------------- Detector (native if complete, else lazy ZXing-wasm)

type NativeBarcodeDetectorCtor = {
  new (opts?: { formats?: readonly string[] }): BarcodeDetectorLike;
  getSupportedFormats?: () => Promise<string[]>;
};

let detectorPromise: Promise<BarcodeDetectorLike> | null = null;

async function createDetector(): Promise<BarcodeDetectorLike> {
  const nativeCtor =
    typeof window !== 'undefined'
      ? (window as unknown as { BarcodeDetector?: NativeBarcodeDetectorCtor }).BarcodeDetector
      : undefined;

  if (nativeCtor && typeof nativeCtor.getSupportedFormats === 'function') {
    try {
      const supported = new Set(await nativeCtor.getSupportedFormats());
      if (BARCODE_FORMATS.every((f) => supported.has(f))) {
        return new nativeCtor({ formats: [...BARCODE_FORMATS] });
      }
    } catch {
      // Fall through to ZXing-wasm ponyfill.
    }
  }

  const [{ BarcodeDetector, setZXingModuleOverrides, prepareZXingModule }, wasmMod] = await Promise.all([
    import('barcode-detector/ponyfill'),
    import('zxing-wasm/reader/zxing_reader.wasm?url'),
  ]);
  const wasmUrl = wasmMod.default;
  const overrides = {
    locateFile: (path: string, prefix: string) => (path.endsWith('.wasm') ? wasmUrl : prefix + path),
  };
  setZXingModuleOverrides(overrides);
  await prepareZXingModule({ overrides, fireImmediately: true });
  return new BarcodeDetector({ formats: [...BARCODE_FORMATS] }) as BarcodeDetectorLike;
}

/** Returns a shared BarcodeDetector (native when all formats are supported, else lazy-loaded self-hosted ZXing-wasm). */
export function getBarcodeDetector(): Promise<BarcodeDetectorLike> {
  detectorPromise ??= createDetector().catch((err) => {
    detectorPromise = null;
    throw err;
  });
  return detectorPromise;
}

/** Decodes a still image file (e.g. from <input type="file">) using the same detector. Never persists the photo. */
export async function decodeBarcodeFromImage(file: Blob): Promise<string | null> {
  const detector = await getBarcodeDetector();
  let closeSource: () => void = () => {};
  let source: CanvasImageSource | ImageBitmap;

  if (typeof createImageBitmap === 'function') {
    try {
      const bmp = await createImageBitmap(file, { imageOrientation: 'from-image' });
      source = bmp;
      closeSource = () => bmp.close();
    } catch {
      const loaded = await loadImgElement(file);
      source = loaded.img;
      closeSource = loaded.close;
    }
  } else {
    const loaded = await loadImgElement(file);
    source = loaded.img;
    closeSource = loaded.close;
  }

  try {
    const hits = await detector.detect(source);
    for (const hit of hits) {
      if (!hit.rawValue) continue;
      const normalized = normalizeScannedCode(hit.rawValue, hit.format);
      if (normalized) return normalized;
    }
    return null;
  } finally {
    closeSource();
  }
}

async function loadImgElement(file: Blob): Promise<{ img: HTMLImageElement; close: () => void }> {
  const url = URL.createObjectURL(file);
  const img = new Image();
  img.src = url;
  try {
    await img.decode();
    return { img, close: () => URL.revokeObjectURL(url) };
  } catch (err) {
    URL.revokeObjectURL(url);
    throw err;
  }
}

// ---------------------------------------------------------------- Open Food Facts v2 & Lookup

const num = (v: unknown): number | null => {
  const n = typeof v === 'string' ? Number(v) : v;
  return typeof n === 'number' && Number.isFinite(n) && n >= 0 ? n : null;
};

/**
 * Repairs lost decimal points in per-100g Open Food Facts macros (port of Android OpenFoodFacts.repair).
 * Missing counts as 0; unfixable >100g values return null.
 */
export function repairPer100g(v: number | null, kcal: number): number | null {
  if (v == null) return 0;
  if (v >= 0 && v <= 100) return v;
  const fixed = [v / 100, v / 10].find((c) => c >= 0 && c <= 100 && c * 4 <= kcal * 1.2 + 5);
  return fixed ?? null;
}

const str = (v: unknown): string => (typeof v === 'string' ? v.trim() : '');

function deriveServingLabel(p: Record<string, unknown>, useServing: boolean): string {
  const servingSize = str(p.serving_size);
  if (useServing) return servingSize;

  const quantityStr = str(p.quantity).toLowerCase();
  const written = /^\s*(\d+(?:[.,]\d+)?)\s*(ml|cl|dl|l|g|kg)(?![a-z])/.exec(quantityStr);
  const unit = str(p.product_quantity_unit).toLowerCase() || (written?.[2] ?? '');
  const liquid = unit === 'ml' || unit === 'cl' || unit === 'l' || unit === 'dl';
  return liquid ? '100 ml' : '100 g';
}

/** Parses an Open Food Facts v2 product JSON payload into a BarcodeProduct, or null if not found / unusable. */
export function parseOpenFoodFacts(code: string, json: unknown, lang: Lang = getLang()): BarcodeProduct | null {
  if (typeof json !== 'object' || json === null) return null;
  const root = json as Record<string, unknown>;
  if (root.status === 0 || root.status === '0' || root.status === 'failure' || root.status !== 1) return null;
  if (typeof root.product !== 'object' || root.product === null) return null;
  const p = root.product as Record<string, unknown>;
  const nut = (typeof p.nutriments === 'object' && p.nutriments !== null ? p.nutriments : null) as
    | Record<string, unknown>
    | null;
  if (!nut) return null;

  const kcal100 =
    num(nut['energy-kcal_100g']) ??
    (() => {
      const kj = num(nut['energy-kj_100g']) ?? num(nut.energy_100g);
      return kj != null ? kj / KJ_PER_KCAL : null;
    })();
  const kcalServing = num(nut['energy-kcal_serving']);
  const useServing = kcalServing != null && kcal100 == null;
  const kcal = useServing ? kcalServing : kcal100 ?? num(nut['energy-kcal']);
  if (kcal == null || kcal < 0) return null;

  let protein: number | null;
  let carbs: number;
  if (useServing) {
    protein = num(nut.proteins_serving) ?? num(nut.proteins) ?? 0;
    carbs = num(nut.carbohydrates_serving) ?? num(nut.carbohydrates) ?? 0;
  } else {
    protein = repairPer100g(num(nut.proteins_100g) ?? num(nut.proteins), kcal);
    if (protein == null) return null;
    carbs = repairPer100g(num(nut.carbohydrates_100g) ?? num(nut.carbohydrates), kcal) ?? 0;
  }

  const nameEn = str(p.product_name);
  const nameAr = str(p.product_name_ar);
  const name = lang === 'ar' ? nameAr || nameEn : nameEn || nameAr;
  const brand = str(p.brands).split(',')[0].trim();
  const serving = deriveServingLabel(p, useServing);

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

type OffFetchResult =
  | { kind: 'found'; product: BarcodeProduct }
  | { kind: 'not_found' }
  | { kind: 'offline' };

async function fetchOffOnce(
  code: string,
  timeoutMs: number,
  outerSignal?: AbortSignal,
): Promise<OffFetchResult> {
  const ctl = new AbortController();
  const onOuter = () => ctl.abort();
  if (outerSignal) {
    if (outerSignal.aborted) return { kind: 'offline' };
    outerSignal.addEventListener('abort', onOuter);
  }
  const timer = setTimeout(() => ctl.abort(), timeoutMs);
  const url = `https://world.openfoodfacts.org/api/v2/product/${encodeURIComponent(code)}?fields=${OFF_FIELDS}`;
  try {
    const res = await fetch(url, { signal: ctl.signal });
    if (res.status === 404) return { kind: 'not_found' };
    if (!res.ok) return { kind: 'offline' };
    const parsed = parseOpenFoodFacts(code, await res.json());
    return parsed ? { kind: 'found', product: parsed } : { kind: 'not_found' };
  } catch {
    return { kind: 'offline' };
  } finally {
    clearTimeout(timer);
    outerSignal?.removeEventListener('abort', onOuter);
  }
}

/** Fetches one code from Open Food Facts v2 with a timeout and a single retry on transient failure/timeout. */
export async function fetchOpenFoodFacts(
  code: string,
  signal?: AbortSignal,
  timeoutMs = OFF_TIMEOUT_MS,
): Promise<OffFetchResult> {
  const first = await fetchOffOnce(code, timeoutMs, signal);
  if (first.kind !== 'offline' || signal?.aborted) return first;
  return fetchOffOnce(code, timeoutMs, signal);
}

/**
 * Looks up a barcode in order:
 * 1. User's saved foods (IndexedDB `db.foods`, offline)
 * 2. Local Open Food Facts cache (IndexedDB `db.kv` `'barcodeCache'`, offline)
 * 3. Open Food Facts v2 API across normalized variants (timeout + 1 retry), caching hits locally.
 */
export async function lookupBarcode(
  raw: string,
  opts?: { signal?: AbortSignal; timeoutMs?: number },
): Promise<BarcodeLookupResult> {
  const variants = barcodeVariants(raw);
  const primary = variants[0] ?? raw.trim();
  if (!variants.length) return { status: 'not_found', code: primary };

  const own = await findFoodByBarcode(variants);
  if (own) return { status: 'own', code: own.barcode?.trim() || primary, food: own };

  const cached = await getCachedBarcodeProduct(variants);
  if (cached) return { status: 'cached', code: cached.code, product: cached };

  for (const variant of variants) {
    if (opts?.signal?.aborted) return { status: 'offline', code: primary };
    const res = await fetchOpenFoodFacts(variant, opts?.signal, opts?.timeoutMs);
    if (res.kind === 'found') {
      await cacheBarcodeProduct(res.product);
      return { status: 'online', code: res.product.code, product: res.product };
    }
    if (res.kind === 'offline') {
      return { status: 'offline', code: primary };
    }
  }

  return { status: 'not_found', code: primary };
}
