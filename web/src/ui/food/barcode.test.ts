import 'fake-indexeddb/auto';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { db } from '../../data/db';
import { saveFood } from '../../data/food';
import {
  barcodeVariants,
  createConsecutiveFilter,
  expandUpcE,
  extractGs1Gtin,
  isValidBarcodeCheckDigit,
  isValidGtinCheckDigit,
  lookupBarcode,
  normalizeScannedCode,
  parseOpenFoodFacts,
  repairPer100g,
} from './barcode';

beforeEach(async () => {
  await Promise.all(db.tables.map((t) => t.clear()));
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('parseOpenFoodFacts', () => {
  it('parses per-serving nutrition when present', () => {
    const product = parseOpenFoodFacts('123456', {
      status: 1,
      product: {
        product_name: 'Greek Yogurt',
        brands: 'Chobani, Other',
        serving_size: '150 g',
        nutriments: {
          'energy-kcal_serving': 130,
          proteins_serving: 15.2,
          carbohydrates_serving: 6.4,
        },
      },
    });
    expect(product).toEqual({
      code: '123456',
      name: 'Greek Yogurt',
      brand: 'Chobani',
      kcal: 130,
      protein: 15.2,
      carbs: 6.4,
      serving: '150 g',
    });
  });

  it('falls back to per-100g when serving is missing and returns null when status != 1', () => {
    expect(parseOpenFoodFacts('999', { status: 0 })).toBeNull();
    const p = parseOpenFoodFacts('111', {
      status: 1,
      product: {
        product_name: 'Oats',
        nutriments: { 'energy-kcal_100g': 379, proteins_100g: 13.2, carbohydrates_100g: 67.7 },
      },
    });
    expect(p?.kcal).toBe(379);
    expect(p?.serving).toBe('100 g');
  });

  it('converts kJ to kcal, repairs misplaced decimals, picks Arabic name, and detects liquids', () => {
    expect(repairPer100g(1001, 400)).toBeCloseTo(10.01);
    expect(repairPer100g(9999, 10)).toBeNull();

    const p = parseOpenFoodFacts(
      '6281007001234',
      {
        status: 1,
        product: {
          product_name: 'Almarai Fresh Milk',
          product_name_ar: 'حليب طازج المراعي',
          brands: 'Almarai',
          product_quantity: 500,
          product_quantity_unit: 'ml',
          nutriments: {
            'energy-kj_100g': 251.04,
            proteins_100g: 3.2,
            carbohydrates_100g: 480, // 4.80 typo
          },
        },
      },
      'ar',
    );
    expect(p).toEqual({
      code: '6281007001234',
      name: 'حليب طازج المراعي',
      brand: 'Almarai',
      kcal: 60,
      protein: 3.2,
      carbs: 4.8,
      serving: '100 ml',
    });
  });
});

describe('check digits & UPC-E expansion', () => {
  it('validates GTIN-8, UPC-A (12), EAN-13, and GTIN-14 check digits', () => {
    expect(isValidGtinCheckDigit('96385074')).toBe(true); // EAN-8
    expect(isValidGtinCheckDigit('96385075')).toBe(false);
    expect(isValidGtinCheckDigit('036000291452')).toBe(true); // UPC-A
    expect(isValidGtinCheckDigit('036000291459')).toBe(false);
    expect(isValidGtinCheckDigit('4006381333931')).toBe(true); // EAN-13
    expect(isValidGtinCheckDigit('4006381333930')).toBe(false);
    expect(isValidGtinCheckDigit('04006381333931')).toBe(true); // GTIN-14
    expect(isValidGtinCheckDigit('12345')).toBe(false);
  });

  it('expands UPC-E to UPC-A for all compression rules and validates check digits', () => {
    expect(expandUpcE('04252614')).toBe('042100005264'); // mode 1
    expect(expandUpcE('04210005')).toBe('042000001005'); // mode 0
    expect(expandUpcE('01234527')).toBe('012200003457'); // mode 2
    expect(expandUpcE('01234530')).toBe('012300000450'); // mode 3
    expect(expandUpcE('01234549')).toBe('012340000059'); // mode 4
    expect(expandUpcE('14252651')).toBe('142526000051'); // mode 5-9, ns=1
    expect(expandUpcE('24252614')).toBeNull(); // invalid number system
    expect(expandUpcE('0425261')).toBeNull(); // wrong length

    expect(isValidBarcodeCheckDigit('04252614', 'upc_e')).toBe(true);
    expect(isValidBarcodeCheckDigit('04252619', 'upc_e')).toBe(false);
  });
});

describe('GS1 GTIN extraction & normalisation variants', () => {
  it('extracts GTIN from GS1 DataMatrix / QR AI (01) and Digital Link URLs', () => {
    expect(extractGs1Gtin('(01)04006381333931(17)260101(10)LOT1')).toBe('4006381333931');
    expect(extractGs1Gtin(']d2010400638133393110ABC')).toBe('4006381333931');
    expect(extractGs1Gtin('\u001d010400638133393117260101')).toBe('4006381333931');
    expect(extractGs1Gtin('https://id.gs1.org/01/04006381333931/10/ABC?17=260101')).toBe('4006381333931');

    // Invalid check digit or plain non-GTIN URLs are rejected
    expect(extractGs1Gtin('(01)04006381333930')).toBeNull();
    expect(extractGs1Gtin('https://example.com/menu')).toBeNull();
    expect(normalizeScannedCode('https://example.com/menu', 'qr_code')).toBeNull();
    expect(normalizeScannedCode('(01)04006381333931(17)260101', 'data_matrix')).toBe('4006381333931');
    expect(normalizeScannedCode('4006381333930', 'ean_13')).toBeNull();
    expect(normalizeScannedCode('4006381333931', 'ean_13')).toBe('4006381333931');
  });

  it('produces normalisation variants for UPC-A, EAN-13, UPC-E, and GTIN-14', () => {
    expect(barcodeVariants('036000291452')).toEqual(['036000291452', '0036000291452']);
    expect(barcodeVariants('0036000291452')).toEqual(['0036000291452', '036000291452']);
    expect(barcodeVariants('04252614')).toEqual(['04252614', '042100005264', '0042100005264']);
    expect(barcodeVariants('04006381333931')).toEqual(['4006381333931', '04006381333931']);
    expect(barcodeVariants('4006381333931')).toEqual(['4006381333931']);
  });
});

describe('consecutive frame filter', () => {
  it('requires 2 consecutive identical codes and resets on miss or mismatch', () => {
    const f = createConsecutiveFilter(2);
    expect(f.push('4006381333931')).toBeNull();
    expect(f.push(null)).toBeNull();
    expect(f.push('4006381333931')).toBeNull();
    expect(f.push('036000291452')).toBeNull();
    expect(f.push('036000291452')).toBe('036000291452');
  });
});

describe('lookupBarcode cache hit/miss & retries', () => {
  it('finds own saved food offline before cache or network', async () => {
    await saveFood({
      name: 'My Protein Bar',
      category: 'SNACKS',
      kcal: 210,
      protein: 20,
      carbs: 18,
      serving: '1 bar',
      photoPath: null,
      pinned: false,
      createdAt: 1,
      barcode: '0036000291452',
    });
    const fetchSpy = vi.fn();
    vi.stubGlobal('fetch', fetchSpy);

    const res = await lookupBarcode('036000291452');
    expect(res.status).toBe('own');
    if (res.status === 'own') expect(res.food.name).toBe('My Protein Bar');
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('caches Open Food Facts hits in IndexedDB so repeat scans work offline', async () => {
    const payload = {
      status: 1,
      product: {
        product_name: 'Labneh',
        brands: 'Nada',
        nutriments: { 'energy-kcal_100g': 160, proteins_100g: 7, carbohydrates_100g: 4 },
      },
    };
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(payload), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    const first = await lookupBarcode('036000291452');
    expect(first.status).toBe('online');
    expect(fetchMock).toHaveBeenCalledTimes(1);

    // Second scan with network offline still succeeds from IndexedDB cache, even via EAN-13 variant
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));
    const second = await lookupBarcode('0036000291452');
    expect(second.status).toBe('cached');
    if (second.status === 'cached') {
      expect(second.product.name).toBe('Labneh');
      expect(second.product.kcal).toBe(160);
    }
  });

  it('retries once on transient failure and does not retry 404', async () => {
    const okPayload = {
      status: 1,
      product: {
        product_name: 'Oat Milk',
        nutriments: { 'energy-kcal_100g': 45, proteins_100g: 1, carbohydrates_100g: 6.5 },
      },
    };
    const retryMock = vi
      .fn()
      .mockRejectedValueOnce(new TypeError('network blip'))
      .mockResolvedValueOnce(new Response(JSON.stringify(okPayload), { status: 200 }));
    vi.stubGlobal('fetch', retryMock);

    const res = await lookupBarcode('4006381333931', { timeoutMs: 200 });
    expect(res.status).toBe('online');
    expect(retryMock).toHaveBeenCalledTimes(2);

    const notFoundMock = vi.fn().mockResolvedValue(new Response('{}', { status: 404 }));
    vi.stubGlobal('fetch', notFoundMock);
    const miss = await lookupBarcode('4006381333931'.replace('4', '5').slice(0, -1) + '0');
    expect(miss.status).toBe('not_found');
    expect(notFoundMock).toHaveBeenCalledTimes(1);
  });
});
