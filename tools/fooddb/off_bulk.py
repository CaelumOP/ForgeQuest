"""Gulf products from the Open Food Facts CSV export (cache/off.csv.gz, ~1.5 GB, from
https://openfoodfacts-ds.s3.eu-west-3.amazonaws.com/en.openfoodfacts.org.products.csv.gz). Data licence: ODbL.

1. Products sold in Saudi Arabia, UAE, Kuwait, Bahrain, Qatar or Oman.
2. Missing calories/protein are copied from a "twin": the same brand + name (+ size first) anywhere in the export.
3. Arabic names from the product name when it's Arabic, or from cached search pages (cache/*.json) when present.
4. Sanity limits per 100 g; writes out/off.csv (SCHEMA.md) and out/off_coverage.txt.
"""
import csv, glob, json, pathlib, re, duckdb

D = pathlib.Path(__file__).parent
C = duckdb.connect()
C.execute("SET threads=8")
C.execute(f"""
create table p as select code, product_name as name, brands, quantity, serving_quantity,
  countries_tags, try_cast(unique_scans_n as int) as scans,
  coalesce(try_cast("energy-kcal_100g" as double), try_cast(energy_100g as double)/4.184) as kcal,
  try_cast(proteins_100g as double) as protein, try_cast(carbohydrates_100g as double) as carbs
from read_csv('{(D / 'cache' / 'off.csv.gz').as_posix()}', delim='\t', quote='', header=true, all_varchar=true,
  ignore_errors=true, max_line_size=10000000)
where code is not null and product_name is not null and product_name <> ''
""")
C.execute("""create table p2 as select *,
  lower(regexp_replace(split_part(coalesce(brands,''),',',1),'[^[:alnum:]]','','g')) || '|' ||
  lower(regexp_replace(name,'[^[:alnum:]]','','g')) as k,
  lower(regexp_replace(coalesce(quantity,''),'[^[:alnum:].]','','g')) as q,
  (kcal between 0 and 900 and protein between 0 and 100 and coalesce(carbs,0) between 0 and 100) as ok
from p""")
GULF = "regexp_matches(countries_tags,'en:(saudi-arabia|united-arab-emirates|kuwait|bahrain|qatar|oman)')"
C.execute(f"create table g as select * from p2 where {GULF}")
C.execute("create table donors as select k, q, kcal, protein, carbs, code as dcode, scans from p2 where ok and length(k) > 4")
C.execute("""create table g2 as
select g.*, coalesce(t1.kcal, t2.kcal) as tk, coalesce(t1.protein, t2.protein) as tp, coalesce(t1.carbs, t2.carbs) as tc,
       coalesce(t1.dcode, t2.dcode) as tcode
from g
left join (select k, q, arg_max(kcal,coalesce(scans,0)) kcal, arg_max(protein,coalesce(scans,0)) protein,
                  arg_max(carbs,coalesce(scans,0)) carbs, arg_max(dcode,coalesce(scans,0)) dcode from donors group by k,q) t1
  on not g.ok and t1.k = g.k and t1.q = g.q and g.q <> ''
left join (select k, arg_max(kcal,coalesce(scans,0)) kcal, arg_max(protein,coalesce(scans,0)) protein,
                  arg_max(carbs,coalesce(scans,0)) carbs, arg_max(dcode,coalesce(scans,0)) dcode from donors group by k) t2
  on not g.ok and t2.k = g.k""")
rows = C.execute("""select code, name, split_part(coalesce(brands,''),',',1), serving_quantity, coalesce(scans,0),
  ok, kcal, protein, carbs, tk, tp, tc, tcode from g2 qualify row_number() over (partition by code order by scans desc nulls last) = 1""").fetchall()

ar_cache = {}
for f in glob.glob(str(D / "cache" / "*.json")):
    try:
        for h in json.load(open(f, encoding="utf-8")).get("hits", []):
            if h.get("product_name_ar"):
                ar_cache[str(h.get("code"))] = h["product_name_ar"].strip()
    except Exception:
        pass
ARABIC = re.compile(r"[؀-ۿ]")
BRAND_AR = {r["brand"]: r["brand_ar"] for r in csv.DictReader(open(D / "sources" / "brands_ar.csv", encoding="utf-8"))}

out, before, twins, arab = [], 0, 0, 0
missing = []
for code, name, brand, serving, scans, ok, kcal, protein, carbs, tk, tp, tc, tcode in rows:
    src = "off"
    if ok:
        before += 1
    elif tk is not None and tp is not None:
        kcal, protein, carbs, src = tk, tp, tc, f"off-twin:{tcode}"; twins += 1
    else:
        missing.append((scans, code, name)); continue
    name = name.strip()
    name_ar = name if ARABIC.search(name) else ar_cache.get(code, "")
    name_en = "" if ARABIC.search(name) else name
    arab += bool(name_ar)
    try:
        sg = float(serving) if serving else ""
        sg = sg if sg and 0 < sg < 3000 else ""
    except ValueError:
        sg = ""
    out.append({"kind": "product", "barcode": re.sub(r"\D", "", code), "name_en": name_en, "name_ar": name_ar,
                "brand": brand.strip(), "brand_ar": BRAND_AR.get(brand.strip().lower(), ""), "kcal": round(kcal, 1), "protein": round(protein, 1),
                "carbs": "" if carbs is None else round(carbs, 1), "serving_g": sg, "units": "", "source": src,
                "scans": scans})

(D / "out").mkdir(exist_ok=True)
with open(D / "out" / "off.csv", "w", encoding="utf-8", newline="") as f:
    w = csv.DictWriter(f, fieldnames=list(out[0].keys())); w.writeheader(); w.writerows(out)
missing.sort(reverse=True)
rep = [f"Gulf products: {len(rows)}", f"with kcal+protein as published: {before}", f"filled from twins: {twins}",
       f"written: {len(out)} ({len(out) * 100 // max(len(rows), 1)}%)", f"with Arabic name: {arab}",
       f"still missing: {len(missing)}; top 30 by scans:"] + [f"  {c} | {n[:50]} | {s}" for s, c, n in missing[:30]]
(D / "out" / "off_coverage.txt").write_text("\n".join(rep), encoding="utf-8")
print("\n".join(rep[:6]))
