#!/usr/bin/env python3
"""Audit: every catalog row resolves to an existing <key>_0.webp and <key>_1.webp on both platforms."""
import re, sys
from pathlib import Path
R = Path(__file__).resolve().parents[2]
KT = R/"app/src/main/java/com/forgequest/app/data/seed"
def kotlin():
    rows = {}
    for line in (KT/"ExerciseCatalog.kt").read_text(encoding="utf-8").splitlines():
        m = re.match(r'\s*ex\("([^"]+)"', line)
        if m:
            k = re.search(r'mediaKey = "([^"]*)"', line)
            rows[m.group(1)] = k.group(1) if k else ""
    by = dict(re.findall(r'"([^"]+)" to "([^"]*)"', (KT/"ExerciseMedia.kt").read_text(encoding="utf-8").split("mapOf(")[1].split("\n    )")[0]))
    return rows, by
def web():
    txt = (R/"web/src/data/seed/exercise-catalog.ts").read_text(encoding="utf-8")
    rows = dict(re.findall(r"\n    id: '([^']+)',.*?\n    mediaKey: '([^']*)',", txt, re.S))
    by = dict(re.findall(r"'([^']+)': '([^']*)'", (R/"web/src/data/seed/exercise-media.ts").read_text(encoding="utf-8")))
    return rows, by
def check(name, rows, by, d):
    missing = []
    for i, k in rows.items():
        key = k or by.get(i, "")
        if not key or not (d/f"{key}_0.webp").exists() or not (d/f"{key}_1.webp").exists():
            missing.append(i)
    print(f"{name}: {len(rows)} rows, {len(missing)} missing")
    return missing
if __name__ == "__main__":
    a = check("android", *kotlin(), R/"app/src/main/assets/ex_media")
    w = check("web", *web(), R/"web/public/ex-media")
    if "-v" in sys.argv: print(sorted(set(a)|set(w)))
    sys.exit(1 if a or w else 0)
