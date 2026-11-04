"""Lane C: everyday basics from USDA FoodData Central (SR Legacy, public domain).

Run from the project root:  python tools/fooddb/usda_basics.py
Reads basics_list.csv (fdc_id, label_en, name_en, name_ar[, extra_units]) and writes out/basics.csv
per SCHEMA.md. fdc_id pins the SR Legacy food; name_en is the USDA match key used only if fdc_id is
blank/unknown. label_en (the short name people read) is written to the output's name_en column.
extra_units are estimates, used only when USDA has no household portion for the food.
"""
import csv
import difflib
import io
import os
import re
import sys
import urllib.request
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, "cache")
OUT = os.path.join(HERE, "out")
LIST = os.path.join(HERE, "basics_list.csv")
ZIP_URL = "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip"
ZIP_PATH = os.path.join(CACHE, "sr_legacy.zip")

KCAL, PROTEIN, CARBS = "1008", "1003", "1005"
COLUMNS = ["kind", "barcode", "name_en", "name_ar", "brand", "kcal", "protein", "carbs",
           "serving_g", "units", "source", "scans"]

# (label_en, label_ar, regex on USDA portion text), checked in order
UNIT_RULES = [
    ("cup", "كوب", re.compile(r"\bcup\b")),
    ("tablespoon", "ملعقة كبيرة", re.compile(r"\b(tbsp|tablespoon)\b")),
    ("teaspoon", "ملعقة صغيرة", re.compile(r"\b(tsp|teaspoon)\b")),
    ("slice", "شريحة", re.compile(r"\bslice")),
    ("piece", "حبة", re.compile(r"\b(piece|medium|large|small|fruit|egg|fillet|breast|thigh|"
                                r"drumstick|unit|item|clove|stalk|date|olive|link|patty|"
                                r"pita|loaf|roll|bun|each|whole|pod)\b")),
]


def download():
    os.makedirs(CACHE, exist_ok=True)
    if os.path.exists(ZIP_PATH):
        return
    print("Downloading", ZIP_URL)
    tmp = ZIP_PATH + ".part"
    with urllib.request.urlopen(ZIP_URL, timeout=120) as r, open(tmp, "wb") as f:
        while True:
            chunk = r.read(1 << 16)
            if not chunk:
                break
            f.write(chunk)
    os.replace(tmp, ZIP_PATH)


def read_table(z, name):
    for n in z.namelist():
        if os.path.basename(n) == name:
            with z.open(n) as f:
                yield from csv.DictReader(io.TextIOWrapper(f, encoding="utf-8-sig"))
            return
    raise SystemExit("missing %s in zip" % name)


def num(s):
    try:
        return float(s)
    except (TypeError, ValueError):
        return None


def fmt(x):
    return ("%.2f" % x).rstrip("0").rstrip(".")


def load_usda():
    z = zipfile.ZipFile(ZIP_PATH)
    foods = {r["fdc_id"]: r["description"] for r in read_table(z, "food.csv")}
    nutrients = {}
    for r in read_table(z, "food_nutrient.csv"):
        if r["nutrient_id"] in (KCAL, PROTEIN, CARBS) and r["fdc_id"] in foods:
            a = num(r["amount"])
            if a is not None:
                nutrients.setdefault(r["fdc_id"], {})[r["nutrient_id"]] = a
    portions = {}
    for r in read_table(z, "food_portion.csv"):
        g, amt = num(r.get("gram_weight")), num(r.get("amount")) or 1.0
        if not g or g <= 0:
            continue
        text = " ".join([r.get("portion_description", ""), r.get("modifier", "")]).lower()
        portions.setdefault(r["fdc_id"], []).append((text, g / amt))
    return foods, nutrients, portions


def match(row, foods, by_desc):
    fid = (row.get("fdc_id") or "").strip()
    if fid in foods:
        return fid, "id"
    key = row["name_en"].strip().lower()
    if key in by_desc:
        return by_desc[key], "exact"
    starts = [d for d in by_desc if d.startswith(key)]
    if starts:
        return by_desc[min(starts, key=len)], "prefix"
    close = difflib.get_close_matches(key, list(by_desc), n=1, cutoff=0.8)
    if close:
        return by_desc[close[0]], "fuzzy"
    return None, None


def build_units(fid, portions, extra):
    units = []
    seen = set()
    for en, ar, rx in UNIT_RULES:
        for text, g in portions.get(fid, []):
            if rx.search(text) and (en == "cup" or "cup" not in text):
                units.append("%s|%s|%s" % (en, ar, fmt(g)))
                seen.add(en)
                break
    for part in ((extra or "") if not units else "").split(";"):
        part = part.strip()
        if part and part.split("|")[0] not in seen:
            units.append(part)
    return units


def main():
    download()
    foods, nutrients, portions = load_usda()
    by_desc = {}
    for fid, d in foods.items():
        by_desc.setdefault(d.lower(), fid)
    rows, unmatched, approx = [], [], []
    with open(LIST, encoding="utf-8-sig", newline="") as f:
        items = list(csv.DictReader(f))
    for it in items:
        fid, how = match(it, foods, by_desc)
        n = nutrients.get(fid, {}) if fid else {}
        if not fid or KCAL not in n:
            unmatched.append(it["name_en"])
            continue
        if how in ("prefix", "fuzzy"):
            approx.append("%s -> %s (%s)" % (it["name_en"], foods[fid], how))
        units = build_units(fid, portions, it.get("extra_units"))
        serving = ""
        for pref in ("piece", "slice", "cup"):
            for u in units:
                p = u.split("|")
                if p[0] == pref:
                    serving = p[2]
                    break
            if serving:
                break
        rows.append({
            "kind": "basic", "barcode": "", "name_en": (it.get("label_en") or it["name_en"]).strip(), "name_ar": it["name_ar"],
            "brand": "", "kcal": fmt(n[KCAL]), "protein": fmt(n.get(PROTEIN, 0.0)),
            "carbs": fmt(n.get(CARBS, 0.0)), "serving_g": serving, "units": ";".join(units),
            "source": "usda:" + fid, "scans": 0,
        })
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "basics.csv"), "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=COLUMNS)
        w.writeheader()
        w.writerows(rows)
    print("Wrote %d basics to %s" % (len(rows), os.path.join(OUT, "basics.csv")))
    if approx:
        print("\nApproximate matches (review):")
        for a in approx:
            print("  " + a)
    if unmatched:
        print("\nUnmatched (%d):" % len(unmatched))
        for u in unmatched:
            print("  " + u)


if __name__ == "__main__":
    sys.exit(main())
