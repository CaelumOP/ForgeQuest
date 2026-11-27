#!/usr/bin/env python3
"""
Fills exercise thumbnails that free-exercise-db does not cover, from three more open datasets
(cloned locally, see the *_ROOT paths), plus honest parent re-use and muscle icons.

Never touches the files build_media.py makes: it only writes the keys implied by SPEC, extends
tools/catalog/media_manifest.json (an "extra" section) and writes tools/catalog/media_sources.json.
Run build_media.py first if you rebuild from scratch, then this script.

Usage:  python tools/catalog/build_media_extra.py            # build + manifests
        python tools/catalog/build_media_extra.py --review   # also write review sheets
Env:    FEDB_ROOT, REPDB_ROOT, OEA_ROOT, WG_ROOT, REVIEW_DIR override the default clone paths.
Requires Pillow. Licence texts: tools/catalog/licenses/.
"""
from __future__ import annotations

import json
import os
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from build_media import (ANDROID_MEDIA_DIR, WEB_MEDIA_DIR, MANIFEST_PATH,  # noqa: E402
                         encode_webp_under_ceiling)

FEDB_ROOT = Path(os.environ.get("FEDB_ROOT", "D:/Claude/tmp/fedb/free-exercise-db-main"))
REPDB_ROOT = Path(os.environ.get("REPDB_ROOT", "D:/Claude/tmp/media-src/exercise-dataset"))
OEA_ROOT = Path(os.environ.get("OEA_ROOT", "D:/Claude/tmp/media-src/Open-Exercise-Art"))
WG_ROOT = Path(os.environ.get("WG_ROOT", "D:/Claude/tmp/media-src/workout-guide/packages/workout-guide/assets"))
REVIEW_DIR = Path(os.environ.get("REVIEW_DIR", "D:/Claude/tmp/media-work"))
SOURCES_PATH = HERE / "media_sources.json"

CARD = (0x1E, 0x1F, 0x20)  # DESIGN.md card, dark (web/src/styles.css --card)

REPO_URLS = {
    "fedb": "https://github.com/yuhonas/free-exercise-db",
    "repdb": "https://repdb.co (RepDB Exercise Dataset, free tier)",
    "oea": "https://github.com/alexmollard/Open-Exercise-Art",
    "workout-guide": "https://github.com/bryllim/workout-guide (upstream Everkinetic)",
    "muscle": "https://repdb.co (RepDB Exercise Dataset, free tier, images/muscles)",
    "parent": "n/a (re-uses another move's files)",
}
LICENCES = {
    "fedb": "The Unlicense (Public Domain)",
    "repdb": "RepDB free-tier licence (LICENSE-DATA.md): in-app use with attribution",
    "oea": "CC BY-SA 4.0",
    "workout-guide": "CC BY-SA 4.0",
    "muscle": "RepDB free-tier licence (LICENSE-DATA.md): in-app use with attribution",
    "parent": "same as the re-used move",
}

# id -> spec. kinds:
#   ("fedb", fedb_id)                 two free-exercise-db photos
#   ("repdb", slug)                   RepDB images/flat/<slug>-start + -peak, or -main for both frames
#   ("oea", slug) / ("oea2", a, b)    Open Exercise Art png on the dark card (oea2: a -> _0, b -> _1)
#   ("wg", slug)                      Workout Guide frame-1 -> _0, frame-2 -> _1
#   ("reuse", key, bucket, note)      mediaKey points at an existing key (bucket: fedb|repdb|parent)
#   ("muscle", icon)                  RepDB muscle icon, key muscle_<icon>
SPEC: dict[str, tuple] = {
    # ---- bucket 1 (free-exercise-db) and its re-uses
    "bicycle_crunch": ("fedb", "Air_Bike"),
    "archer_pull_up": ("fedb", "Side_To_Side_Chins"),
    "lunge": ("fedb", "Bodyweight_Walking_Lunge"),
    "reverse_grip_bench_dip": ("fedb", "Bench_Dips"),
    "calf_raise": ("fedb", "Standing_Calf_Raises"),
    "kettlebell_halo": ("reuse", "kb_halo", "repdb", "same exercise as kb_halo (RepDB picture)"),
    "table_inverted_row": ("reuse", "inverted_row", "fedb", "same picture as inverted_row"),
    "pistol_squat": ("reuse", "kb_pistol_squat", "fedb", "same picture as kb_pistol_squat"),
    "kb_sumo_deadlift": ("reuse", "sumo_deadlift", "fedb", "same picture as sumo_deadlift"),
    "weighted_chin_up": ("reuse", "weighted_pull_up", "fedb", "same picture as weighted_pull_up"),
    # ---- RepDB (priority 1 of the open datasets)
    "kb_halo": ("repdb", "kettlebell-halo"),
    "kettlebell_overhead_triceps_extension": ("repdb", "kettlebell-overhead-tricep-extension"),
    "knee_push_up": ("repdb", "knee-push-ups"),
    "pike_push_up": ("repdb", "pike-push-ups"),
    "pseudo_planche_push_up": ("repdb", "pseudo-planche-push-ups"),
    "archer_push_up": ("repdb", "archer-push-ups"),
    "negative_pull_up": ("repdb", "negative-pull-ups"),
    "band_row": ("repdb", "banded-standing-row"),
    "dead_hang": ("repdb", "dead-hang"),
    "flexed_arm_hang": ("repdb", "flexed-arm-hang"),
    "hanging_knee_raise": ("repdb", "hanging-knee-raise"),
    "l_sit": ("repdb", "l-sit"),
    "handstand_hold": ("repdb", "handstand-hold"),
    "hollow_hold": ("repdb", "hollow-body-hold"),
    "incline_reverse_plank": ("repdb", "reverse-plank"),
    "bird_dog": ("repdb", "bird-dog"),
    "copenhagen_plank_short": ("repdb", "copenhagen-plank"),
    "cossack_squat": ("repdb", "cossack-squat"),
    "kb_deadlift": ("repdb", "kettlebell-deadlift"),
    "single_leg_calf_raise": ("repdb", "single-leg-calf-raise"),
    "side_lying_hip_adduction": ("repdb", "side-lying-hip-adduction"),
    "weighted_dip": ("repdb", "weighted-dips"),
    "jumping_jacks": ("repdb", "jumping-jacks"),
    "pigeon_stretch": ("repdb", "pigeon-stretch"),
    "thread_the_needle": ("repdb", "thread-the-needle"),
    "butterfly_stretch": ("repdb", "butterfly-stretch"),
    "frog_stretch": ("repdb", "frog-stretch"),
    # ---- Open Exercise Art (CC BY-SA 4.0)
    "weighted_push_up": ("oea", "weighted-push-up"),
    "l_sit_pull_up": ("oea", "l-sit-pull-up"),
    "band_curl": ("oea", "banded-alternating-curl"),
    "prone_ytw_raise": ("oea2", "prone-y-raise", "prone-t-raise"),
    "reverse_snow_angel": ("oea", "reverse-snow-angel"),
    "tuck_planche": ("oea", "tuck-planche"),
    "tuck_front_lever": ("oea", "tuck-front-lever"),
    "front_lever_row": ("oea", "front-lever-row"),
    "back_lever_tuck": ("oea", "tuck-back-lever"),
    "plank_shoulder_taps": ("oea", "plank-shoulder-tap"),
    "tibialis_raise": ("oea", "tib-raise"),
    # ---- Workout Guide (CC BY-SA 4.0, upstream Everkinetic)
    "doorframe_row": ("wg", "doorway-row"),
    "burpee": ("wg", "burpee"),
    # ---- bucket 3a: honest parent (re-use the parent's key, no new files)
    "kettlebell_halo_with_overhead_extension": ("reuse", "kb_halo", "parent", "halo is the base movement"),
    "wide_knee_push_up": ("reuse", "knee_push_up", "parent", "same push-up, wider hands"),
    "knee_scapular_push_up": ("reuse", "knee_push_up", "parent", "knee push-up position, scapular range"),
    "incline_diamond_knee_push_up": ("reuse", "knee_push_up", "parent", "knee push-up family"),
    "scapular_australian_pull_up": ("reuse", "australian_pull_up", "parent", "Australian pull-up start position"),
    "scapular_australian_chin_up": ("reuse", "australian_pull_up", "parent", "Australian row start position"),
    "australian_chin_up": ("reuse", "australian_pull_up", "parent", "Australian row, underhand grip"),
    "incline_hollow_plank": ("reuse", "plank", "parent", "plank position, hollow-body posture"),
    "copenhagen_plank_long": ("reuse", "copenhagen_plank_short", "parent", "same Copenhagen plank, longer lever"),
    "half_squat": ("reuse", "squat", "parent", "same squat, partial range"),
    "half_wide_squat": ("reuse", "squat", "parent", "same squat, partial range, wider stance"),
    "half_narrow_squat": ("reuse", "squat", "parent", "same squat, partial range, narrower stance"),
    "calf_raise_turned_out": ("reuse", "calf_raise", "parent", "same calf raise, feet turned out"),
    # ---- bucket 3b: target-muscle icon (RepDB images/muscles/<icon>.webp)
    "towel_floor_pull": ("muscle", "rhomboids"),
    "heel_walk": ("muscle", "tibialis-anterior"),
    "band_dorsiflexion": ("muscle", "tibialis-anterior"),
    "shin_stretch": ("muscle", "tibialis-anterior"),
}
# Muscle enum -> RepDB icon (first primary muscle of the move). Only these two are used so far.
MUSCLE_ICON = {"UPPER_BACK": "rhomboids", "TIBIALIS": "tibialis-anterior"}


def _on_card(path: Path) -> Image.Image:
    im = Image.open(path).convert("RGBA")
    bg = Image.new("RGBA", im.size, CARD + (255,))
    bg.alpha_composite(im)
    return bg.convert("RGB")


def _repdb(slug: str) -> list[Path]:
    flat = REPDB_ROOT / "images/flat"
    start, peak, main = flat / f"{slug}-start.webp", flat / f"{slug}-peak.webp", flat / f"{slug}-main.webp"
    if start.exists() and peak.exists():
        return [start, peak]
    if main.exists():
        return [main, main]
    raise FileNotFoundError(f"RepDB {slug}")


def frames_for(spec: tuple, tmp: Path) -> tuple[list[Path], str]:
    """Returns (two source image paths, upstream slug label). Composited images go to tmp as PNG."""
    kind = spec[0]
    if kind == "fedb":
        fedb = {e["id"]: e for e in json.loads((FEDB_ROOT / "dist/exercises.json").read_text(encoding="utf-8"))}
        imgs = fedb[spec[1]]["images"]
        return [FEDB_ROOT / "exercises" / imgs[0], FEDB_ROOT / "exercises" / imgs[1]], spec[1]
    if kind == "repdb":
        return _repdb(spec[1]), spec[1]
    if kind in ("oea", "oea2"):
        out = []
        for slug in (spec[1:3] if kind == "oea2" else spec[1:2] * 2):
            dst = tmp / f"oea_{slug}.png"
            _on_card(OEA_ROOT / "png" / f"{slug}.png").save(dst)
            out.append(dst)
        return out, "+".join(spec[1:])
    if kind == "wg":
        out = []
        for n in (1, 2):
            dst = tmp / f"wg_{spec[1]}_{n}.png"
            _on_card(WG_ROOT / spec[1] / f"frame-{n}.png").save(dst)
            out.append(dst)
        return out, spec[1]
    if kind == "muscle":
        dst = tmp / f"muscle_{spec[1]}.png"
        _on_card(REPDB_ROOT / "images/muscles" / f"{spec[1]}.webp").save(dst)  # icons are transparent
        return [dst, dst], spec[1]
    raise ValueError(kind)


def key_for(mid: str, spec: tuple) -> str:
    if spec[0] == "reuse":
        return spec[1]
    if spec[0] == "muscle":
        return "muscle_" + spec[1].replace("-", "_")
    return mid


def main() -> int:
    sources: dict[str, dict] = {}
    extra_items: dict[str, dict] = {}
    total = 0
    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td)
        for mid, spec in SPEC.items():
            key = key_for(mid, spec)
            kind = spec[0]
            if kind == "reuse":
                bucket = spec[2]
                sources[mid] = {"bucket": bucket, "source": REPO_URLS[bucket], "upstream": spec[1],
                                "licence": LICENCES[bucket], "mediaKey": key, "note": spec[3]}
                continue
            bucket = "workout-guide" if kind == "wg" else ("oea" if kind == "oea2" else kind)
            srcs, label = frames_for(spec, tmp)
            sources[mid] = {"bucket": bucket, "source": REPO_URLS[bucket], "upstream": label,
                            "licence": LICENCES[bucket], "mediaKey": key}
            if key in extra_items:
                continue  # shared muscle icon, written once
            recs = []
            for idx, src in enumerate(srcs):
                data, q, _ms, dims = encode_webp_under_ceiling(src)
                if len(data) > 20000:
                    print(f"ERROR {key}_{idx} {len(data)} bytes", file=sys.stderr)
                    return 1
                fname = f"{key}_{idx}.webp"
                (ANDROID_MEDIA_DIR / fname).write_bytes(data)
                (WEB_MEDIA_DIR / fname).write_bytes(data)
                total += len(data)
                recs.append({"file": fname, "bytes": len(data), "quality": q, "width": dims[0], "height": dims[1]})
            extra_items[key] = {"mediaKey": key, "bucket": bucket, "upstream": label, "frames": recs}

    SOURCES_PATH.write_text(json.dumps(dict(sorted(sources.items())), indent=2, ensure_ascii=False) + "\n",
                            encoding="utf-8")
    man = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    man["extra"] = {
        "note": "Built by tools/catalog/build_media_extra.py; per-move sources in media_sources.json",
        "keys": len(extra_items), "totalBytesPerPlatform": total,
        "items": [extra_items[k] for k in sorted(extra_items)],
    }
    MANIFEST_PATH.write_text(json.dumps(man, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(extra_items)} keys ({len(extra_items) * 2} frames), {total:,} bytes per platform, "
          f"{len(sources)} moves.")
    if "--review" in sys.argv:
        write_review(sources)
    return 0


def write_review(sources: dict[str, dict], per_page: int = 24, cols: int = 4, sz: int = 180) -> None:
    ids = sorted(sources)
    cell_w, cell_h = 2 * sz + 12, sz + 34
    for page in range(0, len(ids), per_page):
        chunk = ids[page:page + per_page]
        rows = (len(chunk) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * cell_w, rows * cell_h), (70, 70, 70))
        d = ImageDraw.Draw(sheet)
        for n, mid in enumerate(chunk):
            s = sources[mid]
            x, y = (n % cols) * cell_w, (n // cols) * cell_h
            d.text((x + 3, y + 2), f"{mid} / {s['bucket']}", fill=(255, 255, 0))
            d.text((x + 3, y + 13), f"{s['upstream']} -> {s['mediaKey']}", fill=(180, 220, 255))
            for j in (0, 1):
                f = WEB_MEDIA_DIR / f"{s['mediaKey']}_{j}.webp"
                im = Image.open(f).convert("RGB").resize((sz, sz))
                sheet.paste(im, (x + j * (sz + 2), y + 26))
        out = REVIEW_DIR / f"review-{page // per_page + 1}.png"
        sheet.save(out)
        print("review sheet", out)


if __name__ == "__main__":
    raise SystemExit(main())
