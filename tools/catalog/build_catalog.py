#!/usr/bin/env python3
"""
Single-source generator for ForgeQuest Android & Web exercise catalogs,
Arabic names, historical/slang aliases, cues, media manifests, and strings_catalog.xml.

Reads existing 186 exercises' base definitions from tools/catalog/base_186.json
(extracted once from the original ExerciseCatalog.kt / ExerciseSwapEngine.kt / ExerciseNamesAr.kt)
plus tools/catalog/existing_overrides.py and tools/catalog/new_exercises.py,
and emits synchronized Kotlin, TypeScript, and XML files.
"""
from __future__ import annotations

import json
import os
import sys
from pathlib import Path
from xml.sax.saxutils import escape

REPO_ROOT = Path(__file__).resolve().parents[2]
TOOLS_DIR = Path(__file__).resolve().parent
FEDB_ROOT = Path(os.environ.get("FEDB_ROOT", "D:/Claude/tmp/fedb/free-exercise-db-main"))


def kt_str(s: str) -> str:
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def ts_str(s: str) -> str:
    return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"


def xml_text(s: str) -> str:
    return escape(s, {"'": "\\'", '"': '\\"'})


def dedupe_aliases(items: list[str], exclude_norm: set[str]) -> list[str]:
    seen: set[str] = set()
    out: list[str] = []
    for raw in items:
        clean = raw.strip()
        if not clean:
            continue
        low = clean.lower()
        if low in seen or low in exclude_norm:
            continue
        seen.add(low)
        out.append(clean)
    return out


def main() -> int:
    sys.path.insert(0, str(TOOLS_DIR))
    from existing_overrides import EXISTING_OVERRIDES
    from new_exercises import NEW_EXERCISES
    from wp1c_exercises import WP1C_EXERCISES
    from wp1c_merges import WP1C_MERGES

    fedb_list: list[dict] = json.loads((FEDB_ROOT / "dist/exercises.json").read_text(encoding="utf-8"))
    fedb_by_id: dict[str, dict] = {e["id"]: e for e in fedb_list}

    merged_aliases_by_target: dict[str, list[str]] = {}
    for fid, target_id in WP1C_MERGES.items():
        if fid in fedb_by_id:
            merged_aliases_by_target.setdefault(target_id, []).append(fedb_by_id[fid]["name"])

    base_path = TOOLS_DIR / "base_186.json"
    base_186: list[dict] = json.loads(base_path.read_text(encoding="utf-8"))

    all_exercises: list[dict] = []
    previous_names_map: dict[str, list[str]] = {}
    levels_by_id: dict[str, str] = {}

    for b in base_186:
        eid = b["id"]
        new_ar, extra_aliases, fedb_id = EXISTING_OVERRIDES[eid]
        ar_name = new_ar if new_ar else b["ar"]
        hist: list[str] = []
        for candidate in b.get("historicalAr", []):
            if candidate and candidate != ar_name and candidate not in hist:
                hist.append(candidate)
        if hist:
            previous_names_map[eid] = hist

        aliases_list = dedupe_aliases(
            hist + extra_aliases + merged_aliases_by_target.get(eid, []),
            {b["name"].lower(), ar_name.lower()},
        )
        if fedb_id and fedb_id in fedb_by_id and fedb_by_id[fedb_id].get("level"):
            levels_by_id[eid] = fedb_by_id[fedb_id]["level"]
        all_exercises.append({
            "id": eid,
            "name": b["name"],
            "ar": ar_name,
            "kind": b["kind"],
            "primary": b["primary"],
            "secondary": b["secondary"],
            "videoExprKt": b.get("videoExprKt"),
            "videoUrl": b.get("videoUrl"),
            "stretch": b.get("stretch", False),
            "equipment": b["equipment"],
            "pattern": b["pattern"],
            "aliases": ", ".join(aliases_list),
            "mediaKey": eid if fedb_id else "",
            "isNew": False,
        })

    for ex in NEW_EXERCISES + WP1C_EXERCISES:
        eid = ex["id"]
        aliases_list = dedupe_aliases(
            list(ex["aliases"]) + merged_aliases_by_target.get(eid, []),
            {ex["name"].lower(), ex["ar"].lower()},
        )
        fid = ex.get("fedb")
        if fid and fid in fedb_by_id and fedb_by_id[fid].get("level"):
            levels_by_id[eid] = fedb_by_id[fid]["level"]
        has_media = ex.get("has_media", True)
        all_exercises.append({
            "id": eid,
            "name": ex["name"],
            "ar": ex["ar"],
            "kind": ex["kind"],
            "primary": ex["primary"],
            "secondary": ex["secondary"],
            "videoExprKt": None,
            "videoUrl": None,
            "stretch": ex.get("stretch", False),
            "equipment": ex["equipment"],
            "pattern": ex["pattern"],
            "aliases": ", ".join(aliases_list),
            "mediaKey": eid if has_media else "",
            "cues_en": ex["cues_en"],
            "cues_ar": ex["cues_ar"],
            "isNew": True,
        })

    # Validate
    ids = [e["id"] for e in all_exercises]
    assert len(ids) == len(set(ids)), "Duplicate exercise IDs"
    for e in all_exercises:
        if e["mediaKey"]:
            for idx in (0, 1):
                p_and = REPO_ROOT / f"app/src/main/assets/ex_media/{e['mediaKey']}_{idx}.webp"
                p_web = REPO_ROOT / f"web/public/ex-media/{e['mediaKey']}_{idx}.webp"
                assert p_and.exists(), f"Missing Android asset {p_and}"
                assert p_web.exists(), f"Missing Web asset {p_web}"

    # 1. Android ExerciseCatalog.kt
    stretches = [e for e in all_exercises if e["stretch"]]
    mains = [e for e in all_exercises if not e["stretch"]]

    def kt_ex_line(e: dict) -> str:
        prim = "listOf(" + ", ".join(e["primary"]) + ")"
        parts = [kt_str(e["id"]), kt_str(e["name"]), e["kind"], prim]
        if e["secondary"]:
            parts.append("listOf(" + ", ".join(e["secondary"]) + ")")
        if e["videoExprKt"]:
            if not e["secondary"]:
                parts.append("video = " + e["videoExprKt"])
            else:
                parts.append(e["videoExprKt"])
        if e["equipment"]:
            parts.append(f'equipment = {kt_str(e["equipment"])}')
        if e["pattern"]:
            parts.append(f'pattern = {kt_str(e["pattern"])}')
        if e["aliases"]:
            parts.append(f'aliases = {kt_str(e["aliases"])}')
        if e["mediaKey"]:
            parts.append(f'mediaKey = {kt_str(e["mediaKey"])}')
        return "        ex(" + ", ".join(parts) + "),"

    # Chunk main exercises to stay well under the JVM 64 KB method bytecode limit.
    chunk_size = 120
    main_chunks = [mains[i : i + chunk_size] for i in range(0, len(mains), chunk_size)]
    chunk_fns: list[str] = []
    for idx, chunk in enumerate(main_chunks):
        chunk_fns.append(f"    private fun part{idx}(): List<ExerciseEntity> = listOf(")
        chunk_fns.extend(kt_ex_line(e) for e in chunk)
        chunk_fns.append("    )")
        chunk_fns.append("")
    all_parts_expr = " + ".join(f"part{idx}()" for idx in range(len(main_chunks))) + " + stretches"

    kt_catalog = [
        "package com.forgequest.app.data.seed",
        "",
        "import com.forgequest.app.data.db.ExerciseEntity",
        "import com.forgequest.app.domain.ExerciseKind",
        "import com.forgequest.app.domain.ExerciseKind.HOLD",
        "import com.forgequest.app.domain.ExerciseKind.REPS",
        "import com.forgequest.app.domain.ExerciseKind.WEIGHTED",
        "import com.forgequest.app.domain.Muscle",
        "import com.forgequest.app.domain.Muscle.ABS",
        "import com.forgequest.app.domain.Muscle.ADDUCTORS",
        "import com.forgequest.app.domain.Muscle.BICEPS",
        "import com.forgequest.app.domain.Muscle.CALVES",
        "import com.forgequest.app.domain.Muscle.CHEST",
        "import com.forgequest.app.domain.Muscle.FOREARMS",
        "import com.forgequest.app.domain.Muscle.GLUTES",
        "import com.forgequest.app.domain.Muscle.HAMSTRINGS",
        "import com.forgequest.app.domain.Muscle.LOWER_BACK",
        "import com.forgequest.app.domain.Muscle.OBLIQUES",
        "import com.forgequest.app.domain.Muscle.QUADS",
        "import com.forgequest.app.domain.Muscle.SHOULDERS",
        "import com.forgequest.app.domain.Muscle.TIBIALIS",
        "import com.forgequest.app.domain.Muscle.TRAPS",
        "import com.forgequest.app.domain.Muscle.TRICEPS",
        "import com.forgequest.app.domain.Muscle.UPPER_BACK",
        "",
        "/** Built-in exercise library: Hemo's foundation moves, calisthenics, gym staples, and free-exercise-db catalog. */",
        "object ExerciseCatalog {",
        "",
        "    private fun ex(",
        "        id: String,",
        "        name: String,",
        "        kind: ExerciseKind,",
        "        primary: List<Muscle>,",
        "        secondary: List<Muscle> = emptyList(),",
        "        video: String? = null,",
        '        equipment: String = "",',
        '        pattern: String = "",',
        '        aliases: String = "",',
        '        mediaKey: String = "",',
        "    ) = ExerciseEntity(",
        "        id = id, name = name, kind = kind, primary = primary, secondary = secondary,",
        "        videoUrl = video, custom = false,",
        "        equipment = equipment, pattern = pattern, aliases = aliases, mediaKey = mediaKey,",
        "    )",
        "",
        '    private const val D1 = "--W5i-fWBvY"',
        '    private const val D2 = "OZY8i8OTCYA"',
        '    private const val D3 = "jgfyxLtte1g"',
        '    private const val D4 = "PD6y0qK1pDk"',
        '    private const val D5 = "JX3G94SyBeU"',
        "",
        "    /** A link that opens Hemo's video at the moment the exercise is demonstrated. */",
        '    fun hemo(videoId: String, seconds: Int) = "https://www.youtube.com/watch?v=$videoId&t=${seconds}s"',
        "",
        "    /** Stretches and mobility holds: cool-down material only, never picked for a plan slot, a swap or a record. */",
        "    private val stretches: List<ExerciseEntity> = listOf(",
        *[kt_ex_line(e) for e in stretches],
        "    )",
        "",
        "    val STRETCH_IDS: Set<String> = stretches.mapTo(HashSet()) { it.id }",
        "",
        *chunk_fns,
        f"    val all: List<ExerciseEntity> = {all_parts_expr}",
        "",
        "    val byId: Map<String, ExerciseEntity> by lazy { all.associateBy { it.id } }",
        "}",
        "",
    ]
    (REPO_ROOT / "app/src/main/java/com/forgequest/app/data/seed/ExerciseCatalog.kt").write_text(
        "\n".join(kt_catalog), encoding="utf-8"
    )

    # 2. Android ExerciseNamesAr.kt
    kt_names = [
        "package com.forgequest.app.data.seed",
        "",
        "/** Community & formal Arabic names for the built-in exercises, by id. Shown when the app runs in Arabic. */",
        "object ExerciseNamesAr {",
        "    val byId = mapOf(",
        *[f"        {kt_str(e['id'])} to {kt_str(e['ar'])}," for e in all_exercises],
        "    )",
        "",
        "    /** Primary previous Arabic name per id (kept for direct map callers). */",
        "    val previous = mapOf(",
        *[f"        {kt_str(eid)} to {kt_str(names[0])}," for eid, names in previous_names_map.items()],
        "    )",
        "",
        "    /** All historical Arabic names shipped previously, so upgrades migrate safely without touching user renames. */",
        "    val previousAll: Map<String, List<String>> = mapOf(",
        *[
            f"        {kt_str(eid)} to listOf(" + ", ".join(kt_str(n) for n in names) + "),"
            for eid, names in previous_names_map.items()
        ],
        "    )",
        "",
        "    private val byEnglishLower: Map<String, String> by lazy {",
        "        buildMap {",
        "            for (ex in ExerciseCatalog.all) {",
        "                val ar = byId[ex.id] ?: continue",
        "                put(ex.name.lowercase(), ar)",
        "            }",
        '            byId["back_squat"]?.let { put("barbell back squat", it) }',
        "        }",
        "    }",
        "",
        "    private val byArabic: Map<String, String> by lazy {",
        "        buildMap {",
        "            for (ex in ExerciseCatalog.all) {",
        "                val ar = byId[ex.id] ?: continue",
        "                put(ar, ex.name)",
        "            }",
        "            for ((id, oldList) in previousAll) {",
        "                val en = ExerciseCatalog.byId[id]?.name ?: continue",
        "                for (old in oldList) putIfAbsent(old, en)",
        "            }",
        "        }",
        "    }",
        "",
        "    /** Translates built-in exercise names (such as synced friend PRs) at display time. */",
        "    fun displayName(name: String): String {",
        "        val trimmed = name.trim()",
        '        return if (java.util.Locale.getDefault().language == "ar") {',
        "            byEnglishLower[trimmed.lowercase()] ?: name",
        "        } else {",
        "            byArabic[trimmed] ?: name",
        "        }",
        "    }",
        "}",
        "",
    ]
    (REPO_ROOT / "app/src/main/java/com/forgequest/app/data/seed/ExerciseNamesAr.kt").write_text(
        "\n".join(kt_names), encoding="utf-8"
    )

    # 3. Android ExerciseAliases.kt
    kt_aliases = [
        "package com.forgequest.app.data.seed",
        "",
        "/** Searchable EN abbreviations, Arabic gym slang, and historical names for every built-in exercise. */",
        "object ExerciseAliases {",
        "    val byId: Map<String, String> = mapOf(",
        *[f"        {kt_str(e['id'])} to {kt_str(e['aliases'])}," for e in all_exercises if e["aliases"]],
        "    )",
        "",
        '    fun forId(id: String): String = byId[id].orEmpty()',
        "}",
        "",
    ]
    (REPO_ROOT / "app/src/main/java/com/forgequest/app/data/seed/ExerciseAliases.kt").write_text(
        "\n".join(kt_aliases), encoding="utf-8"
    )

    # 4. Android ExerciseCues.kt (for new 200 exercises; existing 186 are in EnumLabels.kt + strings_cues.xml)
    new_exs = [e for e in all_exercises if e["isNew"]]
    kt_cues = [
        "package com.forgequest.app.data.seed",
        "",
        "import com.forgequest.app.R",
        "",
        "/** Form cues and common mistakes for the 200 v4.1 catalog additions (in strings_catalog.xml). */",
        "object ExerciseCues {",
        "    val addedById: Map<String, Triple<Int, Int, Int>> = mapOf(",
        *[
            f'        {kt_str(e["id"])} to Triple(R.string.cue_{e["id"]}_1, R.string.cue_{e["id"]}_2, R.string.cue_{e["id"]}_mistake),'
            for e in new_exs
        ],
        "    )",
        "",
        "    fun addedCues(id: String): Triple<Int, Int, Int>? = addedById[id]",
        "}",
        "",
    ]
    (REPO_ROOT / "app/src/main/java/com/forgequest/app/data/seed/ExerciseCues.kt").write_text(
        "\n".join(kt_cues), encoding="utf-8"
    )

    # 5. Android ExerciseMedia.kt
    mapped = [e for e in all_exercises if e["mediaKey"]]
    kt_media = [
        "package com.forgequest.app.data.seed",
        "",
        "/** Offline 2-frame WebP asset keys (app/src/main/assets/ex_media/<key>_0.webp & _1.webp). */",
        "object ExerciseMedia {",
        "    val byId: Map<String, String> = mapOf(",
        *[f"        {kt_str(e['id'])} to {kt_str(e['mediaKey'])}," for e in mapped],
        "    )",
        "",
        "    fun mediaKeyFor(id: String): String = byId[id].orEmpty()",
        "",
        "    fun assetPaths(mediaKey: String): Pair<String, String>? {",
        "        val key = mediaKey.trim()",
        '        if (key.isEmpty()) return null',
        '        return "ex_media/${key}_0.webp" to "ex_media/${key}_1.webp"',
        "    }",
        "}",
        "",
    ]
    (REPO_ROOT / "app/src/main/java/com/forgequest/app/data/seed/ExerciseMedia.kt").write_text(
        "\n".join(kt_media), encoding="utf-8"
    )

    # 6. Android strings_catalog.xml (EN + AR)
    en_xml = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    ar_xml = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    en_xml.append('    <string name="catalog_volume_explain">Weight × reps across working sets</string>')
    ar_xml.append('    <string name="catalog_volume_explain">الوزن × العدّات عبر مجموعات العمل</string>')
    en_xml.append('    <string name="catalog_media_placeholder_hint">No offline preview yet. Use Watch form below.</string>')
    ar_xml.append('    <string name="catalog_media_placeholder_hint">لا تتوفر صور دون اتصال بعد. استخدم «مشاهدة الأداء» بالأسفل.</string>')
    for e in new_exs:
        eid = e["id"]
        c1, c2, cm = e["cues_en"]
        a1, a2, am = e["cues_ar"]
        en_xml.append(f'    <string name="cue_{eid}_1">{xml_text(c1)}</string>')
        en_xml.append(f'    <string name="cue_{eid}_2">{xml_text(c2)}</string>')
        en_xml.append(f'    <string name="cue_{eid}_mistake">{xml_text(cm)}</string>')
        ar_xml.append(f'    <string name="cue_{eid}_1">{xml_text(a1)}</string>')
        ar_xml.append(f'    <string name="cue_{eid}_2">{xml_text(a2)}</string>')
        ar_xml.append(f'    <string name="cue_{eid}_mistake">{xml_text(am)}</string>')
    en_xml.append("</resources>")
    ar_xml.append("</resources>")
    (REPO_ROOT / "app/src/main/res/values/strings_catalog.xml").write_text("\n".join(en_xml) + "\n", encoding="utf-8")
    (REPO_ROOT / "app/src/main/res/values-ar/strings_catalog.xml").write_text("\n".join(ar_xml) + "\n", encoding="utf-8")

    # 7. Web exercise-catalog.ts
    ts_cat = [
        "import type { ExerciseEntity } from '../../domain/entities'",
        "",
        "/** Built-in exercise library, synchronized with Android ExerciseCatalog.kt (same order and values). */",
        "export const EXERCISE_CATALOG: ExerciseEntity[] = [",
    ]
    for e in all_exercises:
        prim = "[" + ", ".join(ts_str(m) for m in e["primary"]) + "]"
        sec = "[" + ", ".join(ts_str(m) for m in e["secondary"]) + "]"
        vid = ts_str(e["videoUrl"]) if e["videoUrl"] else "null"
        ts_cat.extend([
            "  {",
            f"    id: {ts_str(e['id'])},",
            f"    name: {ts_str(e['name'])},",
            f"    kind: {ts_str(e['kind'])},",
            f"    primary: {prim},",
            f"    secondary: {sec},",
            f"    videoUrl: {vid},",
            "    custom: false,",
            f"    equipment: {ts_str(e['equipment'])},",
            f"    pattern: {ts_str(e['pattern'])},",
            f"    aliases: {ts_str(e['aliases'])},",
            f"    mediaKey: {ts_str(e['mediaKey'])},",
            "  },",
        ])
    ts_cat.extend([
        "]",
        "",
        "/** Stretches and mobility holds: cool-down material only, never picked for a plan slot, a swap or a record. */",
        "export const STRETCH_IDS: Set<string> = new Set([",
        *[f"  {ts_str(e['id'])}," for e in stretches],
        "])",
        "",
        "export const EXERCISE_BY_ID: Map<string, ExerciseEntity> = new Map(EXERCISE_CATALOG.map((e) => [e.id, e]))",
        "",
    ])
    (REPO_ROOT / "web/src/data/seed/exercise-catalog.ts").write_text("\n".join(ts_cat), encoding="utf-8")

    # 8. Web exercise-names-ar.ts
    ts_ar = [
        "/** Community & formal Arabic names for the built-in exercises, by id (synced with ExerciseNamesAr.kt). */",
        "export const EXERCISE_NAMES_AR: Record<string, string> = {",
        *[f"  {ts_str(e['id'])}: {ts_str(e['ar'])}," for e in all_exercises],
        "}",
        "",
        "/** Historical Arabic names shipped previously, used during seeding upgrade so user renames are preserved. */",
        "export const PREVIOUS_EXERCISE_NAMES_AR: Record<string, readonly string[]> = {",
        *[
            f"  {ts_str(eid)}: [" + ", ".join(ts_str(n) for n in names) + "],"
            for eid, names in previous_names_map.items()
        ],
        "}",
        "",
    ]
    (REPO_ROOT / "web/src/data/seed/exercise-names-ar.ts").write_text("\n".join(ts_ar), encoding="utf-8")

    # 9. Web exercise-aliases.ts
    ts_aliases = [
        "/** Searchable EN abbreviations, Arabic gym slang, and historical names (synced with ExerciseAliases.kt). */",
        "export const EXERCISE_ALIASES: Record<string, string> = {",
        *[f"  {ts_str(e['id'])}: {ts_str(e['aliases'])}," for e in all_exercises if e["aliases"]],
        "}",
        "",
        "export function exerciseAliasesFor(id: string): string {",
        "  return EXERCISE_ALIASES[id] ?? ''",
        "}",
        "",
    ]
    (REPO_ROOT / "web/src/data/seed/exercise-aliases.ts").write_text("\n".join(ts_aliases), encoding="utf-8")

    # 10. Web exercise-cues.ts
    ts_cues = [
        "// Form cues (2 cues + common mistake) for all built-in exercises, mirroring Android strings_cues.xml + strings_catalog.xml.",
        "export const EXERCISE_CUE_KEYS: Record<string, readonly [string, string, string]> = {",
        *[
            f"  {ts_str(e['id'])}: ['cue_{e['id']}_1', 'cue_{e['id']}_2', 'cue_{e['id']}_mistake'],"
            for e in all_exercises
        ],
        "}",
        "",
        "export function exerciseCueKeys(id: string): readonly [string, string, string] | null {",
        "  return EXERCISE_CUE_KEYS[id] ?? null",
        "}",
        "",
    ]
    (REPO_ROOT / "web/src/data/seed/exercise-cues.ts").write_text("\n".join(ts_cues), encoding="utf-8")

    # 11. Web exercise-media.ts
    ts_media = [
        "/** Offline 2-frame WebP image keys (web/public/ex-media/<key>_0.webp & _1.webp). */",
        "export const EXERCISE_MEDIA_KEYS: Record<string, string> = {",
        *[f"  {ts_str(e['id'])}: {ts_str(e['mediaKey'])}," for e in mapped],
        "}",
        "",
        "export function exerciseMediaUrls(mediaKey: string): readonly [string, string] | null {",
        "  const key = mediaKey.trim()",
        "  if (!key) return null",
        "  return [`ex-media/${key}_0.webp`, `ex-media/${key}_1.webp`]",
        "}",
        "",
    ]
    (REPO_ROOT / "web/src/data/seed/exercise-media.ts").write_text("\n".join(ts_media), encoding="utf-8")

    # 12. Android ExerciseLevels.kt + Web exercise-levels.ts
    sorted_levels = sorted(levels_by_id.items())
    kt_levels = [
        "package com.forgequest.app.data.seed",
        "",
        "/** Dataset difficulty level (beginner / intermediate / expert) for all mapped built-in exercises. */",
        "object ExerciseLevels {",
        "    val byId: Map<String, String> = mapOf(",
        *[f"        {kt_str(eid)} to {kt_str(lvl)}," for eid, lvl in sorted_levels],
        "    )",
        "",
        "    fun levelFor(id: String): String = byId[id].orEmpty()",
        "}",
        "",
    ]
    (REPO_ROOT / "app/src/main/java/com/forgequest/app/data/seed/ExerciseLevels.kt").write_text(
        "\n".join(kt_levels), encoding="utf-8"
    )

    ts_levels = [
        "/** Dataset difficulty level ('beginner' | 'intermediate' | 'expert') synced with ExerciseLevels.kt. */",
        "export type ExerciseLevel = 'beginner' | 'intermediate' | 'expert'",
        "",
        "export const EXERCISE_LEVELS: Record<string, ExerciseLevel> = {",
        *[f"  {ts_str(eid)}: {ts_str(lvl)}," for eid, lvl in sorted_levels],
        "}",
        "",
        "export function exerciseLevelFor(id: string): ExerciseLevel | undefined {",
        "  return EXERCISE_LEVELS[id]",
        "}",
        "",
    ]
    (REPO_ROOT / "web/src/data/seed/exercise-levels.ts").write_text("\n".join(ts_levels), encoding="utf-8")

    print(f"Generated Kotlin + TypeScript + XML catalog files for {len(all_exercises)} exercises ({len(mapped)} mapped to WebP media, {len(levels_by_id)} levels, {len(new_exs)} new).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
