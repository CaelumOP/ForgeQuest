"""Saudi dishes from the SFDA Saudi Food Composition Tables (sources/SFCT-E.pdf).

Each dish is a recipe page (ingredients, name, "Approximately N adults") followed by a nutrition page
("Analyte per100g Fullrecipe Unit"). Values are copied exactly as printed; one serving = full recipe / N adults.
Writes out/sfda.csv (SCHEMA.md). Arabic names come from sources/sfda_names_ar.csv when present (name_en,name_ar).
"""
import csv, pathlib, re, sys
import pypdf

D = pathlib.Path(__file__).parent
pdf = pypdf.PdfReader(str(D / "sources" / "SFCT-E.pdf"))
pages = [(p.extract_text() or "") for p in pdf.pages]
ar = {}
names_file = D / "sources" / "sfda_names_ar.csv"
if names_file.exists():
    ar = {r["name_en"]: r["name_ar"] for r in csv.DictReader(names_file.open(encoding="utf-8"))}

def val(text, label):
    m = re.search(rf"^{re.escape(label)}\s+([\d.]+|-)\s+([\d.]+|-)", text, re.M)
    if not m or m.group(1) == "-":
        return None, None
    return float(m.group(1)), (None if m.group(2) == "-" else float(m.group(2)))

rows, problems = [], []
for i, t in enumerate(pages):
    if "Analyte per100g" not in t or i == 0:
        continue
    recipe = pages[i - 1]
    if "Recipe Name" not in recipe and i >= 2:  # long recipes run over two pages
        recipe = pages[i - 2] + "\n" + recipe
    lines = [l.strip() for l in recipe.splitlines() if l.strip()]
    # The name sits just above "Recipe Name", sometimes with "Preparation Method" in between.
    name = None
    skip = re.compile(r"^(Preparation Method|Approximately|Recipe Name)")
    if "Recipe Name" in lines:
        k = lines.index("Recipe Name") - 1
        while k >= 0 and skip.match(lines[k]):
            k -= 1
        name = lines[k] if k >= 0 else None
        if name and name.startswith("with ") and k >= 1:  # "Whole wheat Aseedah" / "with Meat"
            name = lines[k - 1] + " " + name
    adults = re.search(r"Approximately\s+(\d+)\s+adult", recipe)
    kcal, kcal_full = val(t, "Energy (kcal)")
    protein, _ = val(t, "Total Protein")
    carbs, _ = val(t, "Available Carbohydrates")
    if not name or kcal is None:
        problems.append(f"page {i + 1}: name={name!r} kcal={kcal}")
        continue
    serving = None
    if kcal_full and kcal and adults:
        serving = round(kcal_full / kcal * 100 / int(adults.group(1)))
    rows.append({"kind": "dish", "barcode": "", "name_en": name, "name_ar": ar.get(name, ""), "brand": "",
                 "kcal": kcal, "protein": protein if protein is not None else "", "carbs": carbs if carbs is not None else "",
                 "serving_g": serving or "", "units": "", "source": f"sfda:p{i + 1}", "scans": 0})

(D / "out").mkdir(exist_ok=True)
with (D / "out" / "sfda.csv").open("w", encoding="utf-8", newline="") as f:
    w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
    w.writeheader(); w.writerows(rows)
print(f"dishes {len(rows)}, with serving {sum(1 for r in rows if r['serving_g'])}, with Arabic {sum(1 for r in rows if r['name_ar'])}")
for p in problems:
    print("PROBLEM", p)
