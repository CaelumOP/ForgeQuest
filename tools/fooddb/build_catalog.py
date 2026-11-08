"""Merge tools/fooddb/out/*.csv (SCHEMA.md columns) into app/src/main/assets/catalog.db.

Rows without kcal are dropped; products are deduped by barcode (most scans wins). Run: python build_catalog.py
sample.csv (made-up rows for testing) is rewritten each run and merged only with --with-sample or when no real CSV exists.
"""
import csv
import re
import glob
import os
import sqlite3
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "out")
DB = os.path.normpath(os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "catalog.db"))
COLS = ["kind", "barcode", "name_en", "name_ar", "brand", "brand_ar", "kcal", "protein", "carbs", "serving_g", "units", "source", "scans"]

SAMPLE = """kind,barcode,name_en,name_ar,brand,kcal,protein,carbs,serving_g,units,source,scans
product,6281007032013,Almarai Full Fat Milk,حليب المراعي كامل الدسم,Almarai,62,3.1,4.7,250,cup|كوب|240,off,900
product,6281007011056,Almarai Laban,لبن المراعي,Almarai,45,3.3,4.2,200,cup|كوب|240,off,500
product,6281100010013,Nadec Fresh Orange Juice,عصير نادك برتقال,Nadec,45,0.6,10.4,200,cup|كوب|240,off,300
product,5449000000996,Coca-Cola,كوكا كولا,Coca-Cola,42,0,10.6,330,can|علبة|330,off,2000
product,6281062001122,Lays Chips Salt,رقائق ليز بالملح,Lays,536,6.5,52,30,pack|كيس|30,off,800
product,6281026000115,Tamimi Kabsa Rice,أرز كبسة التميمي,Tamimi,355,8,78,,cup|كوب|160,off,40
product,6281001251234,Halwani Hummus,حمص حلواني,Halwani,230,7.5,17,,tbsp|ملعقة كبيرة|15,off,60
product,6281005001012,Kinder Bueno,كيندر بوينو,Kinder,572,8.6,49,43,piece|حبة|21.5,off,700
product,6281036002309,Almarai Fresh Cream Cheese,جبنة المراعي كريمي,Almarai,255,6,4,,slice|شريحة|20,off,200
product,6281000000011,Saudia Dates Ajwa,تمر عجوة,,277,1.8,75,,date|تمرة|10,off,150
dish,,Chicken Kabsa,كبسة دجاج,,168,9,20,450,plate|صحن|450;cup|كوب|160,sfda,0
dish,,Mutton Mandi,مندي لحم,,190,11,21,500,plate|صحن|500;cup|كوب|160,sfda,0
dish,,Jareesh,جريش,,163,8.4,14,300,ladle|مغرفة|120;plate|صحن|300,sfda,0
dish,,Shawarma Chicken Sandwich,ساندويتش شاورما دجاج,,215,13,22,220,piece|حبة|220,sfda,0
dish,,Falafel,فلافل,,333,13,32,,piece|حبة|17,sfda,0
dish,,Foul Medames,فول مدمس,,110,7,15,250,plate|صحن|250;ladle|مغرفة|100,sfda,0
dish,,Harees,هريس,,150,9,17,300,plate|صحن|300,sfda,0
dish,,Samboosa Meat,سمبوسة لحم,,290,9,26,,piece|حبة|35,sfda,0
basic,,Banana,موز,,89,1.1,22.8,118,piece|حبة|118,usda:1105314,0
basic,,Apple,تفاح,,52,0.3,13.8,182,piece|حبة|182,usda:1750340,0
basic,,Egg boiled,بيض مسلوق,,155,12.6,1.1,50,piece|حبة|50,usda:748967,0
basic,,Cooked white rice,أرز أبيض مطبوخ,,130,2.7,28.2,,cup|كوب|160;ladle|مغرفة|90,usda:2512381,0
basic,,Chicken breast grilled,صدر دجاج مشوي,,165,31,0,,piece|قطعة|120,usda:171477,0
basic,,Whole wheat bread,خبز قمح كامل,,252,12.5,42.7,,slice|شريحة|28,usda:172687,0
basic,,Olive oil,زيت زيتون,,884,0,0,,tbsp|ملعقة كبيرة|13.5;tsp|ملعقة صغيرة|4.5,usda:171413,0
basic,,Dates (Medjool),تمر مجدول,,277,1.8,75,,date|تمرة|24,usda:168191,0
basic,,Arabic coffee (qahwa),قهوة عربية,,2,0.1,0,,cup|فنجان|60,usda:171890,0
basic,,Yogurt plain,زبادي سادة,,61,3.5,4.7,,cup|كوب|245;tbsp|ملعقة كبيرة|15,usda:170903,0
"""


def num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def main():
    with open(os.path.join(OUT, "sample.csv"), "w", encoding="utf-8", newline="") as f:
        f.write(SAMPLE)
    with_sample = "--with-sample" in sys.argv
    rows, by_barcode = [], {}
    for path in sorted(glob.glob(os.path.join(OUT, "*.csv"))):
        # sample.csv (made-up barcodes) is merged only with --with-sample, or when no real CSV exists
        real = [q for q in glob.glob(os.path.join(OUT, "*.csv")) if os.path.basename(q) != "sample.csv"]
        if os.path.basename(path) == "sample.csv" and real and not with_sample:
            continue
        with open(path, encoding="utf-8", newline="") as f:
            for r in csv.DictReader(f):
                kcal = num(r.get("kcal"))
                if kcal is None or not (r.get("name_en") or r.get("name_ar")):
                    continue
                code = "".join(ch for ch in (r.get("barcode") or "") if ch.isdigit())
                r["barcode"] = code
                r["scans"] = int(num(r.get("scans")) or 0)
                if code:
                    old = by_barcode.get(code)
                    if old is not None and old["scans"] >= r["scans"]:
                        continue
                    if old is not None:
                        rows.remove(old)
                    by_barcode[code] = r
                rows.append(r)
    drink = re.compile(r"\b(milk|laban|ayran|kefir|juice|nectar|soda|cola|coffee|espresso|latte|cappuccino|tea|soup|broth|stock|water|drink|beverage|lassi|smoothie)\b|حليب|لبن|عصير|شراب|قهوة|شاي|شوربة|حساء|مرق|ماء", re.I)
    notdrink = re.compile(r"in water|syrup|trout|tuna|grilled|fried|بالماء|مرقط|قيقب|chocolate|powder|dried|dry|condensed|cheese|butter|ice cream|bread|cake|yogurt|yoghurt|cream|pudding|مسحوق|جبن|زبدة|آيس|خبز|كعك|زبادي|قشطة", re.I)
    for r in rows:
        if r.get("kind") in ("dish", "basic") and not num(r.get("liquid")):
            t = (r.get("name_en") or "") + " " + (r.get("name_ar") or "")
            if drink.search(t) and not notdrink.search(t):
                r["liquid"] = 1
    os.makedirs(os.path.dirname(DB), exist_ok=True)
    if os.path.exists(DB):
        os.remove(DB)
    con = sqlite3.connect(DB)
    con.execute("CREATE TABLE food(id INTEGER PRIMARY KEY, kind TEXT, barcode TEXT, name_en TEXT, name_ar TEXT, brand TEXT, brand_ar TEXT, "
                "kcal REAL, protein REAL, carbs REAL, serving_g REAL, units TEXT, source TEXT, scans INTEGER, liquid INTEGER DEFAULT 0)")
    con.execute("CREATE INDEX food_barcode ON food(barcode)")
    con.executemany(
        "INSERT INTO food(kind,barcode,name_en,name_ar,brand,brand_ar,kcal,protein,carbs,serving_g,units,source,scans,liquid) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        [(r["kind"], r["barcode"], r.get("name_en", ""), r.get("name_ar", ""), r.get("brand", ""), r.get("brand_ar", ""), num(r["kcal"]),
          num(r.get("protein")) or 0.0, num(r.get("carbs")) or 0.0, num(r.get("serving_g")), r.get("units", ""),
          r.get("source", ""), r["scans"], int(num(r.get("liquid")) or 0)) for r in rows])
    con.commit()
    con.execute("VACUUM")
    con.close()
    print(f"{len(rows)} rows -> {DB} ({os.path.getsize(DB)} bytes)")


if __name__ == "__main__":
    main()
