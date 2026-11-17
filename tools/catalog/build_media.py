#!/usr/bin/env python3
"""
Builds downscaled 2-frame WebP exercise photos from yuhonas/free-exercise-db
into both Android assets (app/src/main/assets/ex_media/) and Web public
(web/public/ex-media/), enforcing <= 20 KB per frame.

Requires: Pillow (PIL) installed in Python environment; source dataset in D:/Claude/tmp/fedb/free-exercise-db-main.
"""
from __future__ import annotations

import io
import json
import os
import shutil
import sys
from pathlib import Path

from PIL import Image

REPO_ROOT = Path(__file__).resolve().parents[2]
FEDB_ROOT = Path(os.environ.get("FEDB_ROOT", "D:/Claude/tmp/fedb/free-exercise-db-main"))
ANDROID_MEDIA_DIR = REPO_ROOT / "app/src/main/assets/ex_media"
WEB_MEDIA_DIR = REPO_ROOT / "web/public/ex-media"
MANIFEST_PATH = REPO_ROOT / "tools/catalog/media_manifest.json"

MAX_SIDE = 360
INITIAL_QUALITY = 60
CEILING_BYTES = 20000  # strictly under 20 KB (20,480 B)
HARD_CAP_TOTAL_BYTES = 18 * 1024 * 1024  # 18 MB total cap across ex_media


def encode_webp_under_ceiling(src_path: Path) -> tuple[bytes, int, int, tuple[int, int]]:
    with Image.open(src_path) as im:
        rgb = im.convert("RGB")
        for max_side in (MAX_SIDE, 320, 280, 240):
            working = rgb.copy()
            working.thumbnail((max_side, max_side), Image.Resampling.LANCZOS)
            for q in (INITIAL_QUALITY, 55, 50, 45, 40, 35, 30, 25, 20):
                buf = io.BytesIO()
                working.save(buf, format="WEBP", quality=q, method=6)
                data = buf.getvalue()
                if len(data) <= CEILING_BYTES:
                    return data, q, max_side, working.size
        # Fallback if still large
        return data, 20, 240, working.size


def main() -> int:
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from existing_overrides import EXISTING_OVERRIDES
    from new_exercises import NEW_EXERCISES
    from wp1c_exercises import WP1C_EXERCISES

    fedb_json = FEDB_ROOT / "dist/exercises.json"
    if not fedb_json.exists():
        print(f"ERROR: Missing dataset at {fedb_json}", file=sys.stderr)
        return 1

    fedb = {e["id"]: e for e in json.loads(fedb_json.read_text(encoding="utf-8"))}

    # Collect mediaKey -> fedb_id
    media_map: dict[str, str] = {}
    for eid, (_ar, _aliases, fid) in EXISTING_OVERRIDES.items():
        if fid:
            media_map[eid] = fid
    for ex in NEW_EXERCISES:
        media_map[ex["id"]] = ex["fedb"]
    for ex in WP1C_EXERCISES:
        if ex.get("has_media", True):
            media_map[ex["id"]] = ex["fedb"]

    ANDROID_MEDIA_DIR.mkdir(parents=True, exist_ok=True)
    WEB_MEDIA_DIR.mkdir(parents=True, exist_ok=True)

    expected_files: set[str] = set()
    manifest: list[dict[str, object]] = []
    total_bytes = 0
    max_frame_bytes = 0
    reencoded_count = 0

    for media_key in sorted(media_map):
        fid = media_map[media_key]
        entry = fedb.get(fid)
        if not entry or len(entry.get("images", [])) < 2:
            print(f"ERROR: {media_key} -> {fid} missing 2 images", file=sys.stderr)
            return 1

        frame_records = []
        for idx in (0, 1):
            src = FEDB_ROOT / "exercises" / entry["images"][idx]
            if not src.exists():
                print(f"ERROR: Missing source image {src}", file=sys.stderr)
                return 1
            data, q, max_side, dims = encode_webp_under_ceiling(src)
            if len(data) > CEILING_BYTES:
                print(f"ERROR: {media_key}_{idx}.webp exceeded ceiling ({len(data)} bytes)", file=sys.stderr)
                return 1
            if q < INITIAL_QUALITY:
                reencoded_count += 1
            fname = f"{media_key}_{idx}.webp"
            expected_files.add(fname)
            (ANDROID_MEDIA_DIR / fname).write_bytes(data)
            (WEB_MEDIA_DIR / fname).write_bytes(data)
            total_bytes += len(data)
            max_frame_bytes = max(max_frame_bytes, len(data))
            frame_records.append({
                "file": fname,
                "bytes": len(data),
                "quality": q,
                "width": dims[0],
                "height": dims[1],
            })

        manifest.append({
            "mediaKey": media_key,
            "fedbId": fid,
            "frames": frame_records,
        })

    # Clean stale files
    for folder in (ANDROID_MEDIA_DIR, WEB_MEDIA_DIR):
        for p in folder.iterdir():
            if p.is_file() and p.name not in expected_files:
                p.unlink()

    MANIFEST_PATH.write_text(json.dumps({
        "source": "https://github.com/yuhonas/free-exercise-db",
        "license": "The Unlicense (Public Domain)",
        "exercisesMapped": len(manifest),
        "totalFrames": len(expected_files),
        "totalBytesPerPlatform": total_bytes,
        "totalMBPerPlatform": round(total_bytes / (1024 * 1024), 2),
        "maxFrameBytes": max_frame_bytes,
        "reencodedBelow60": reencoded_count,
        "items": manifest,
    }, indent=2) + "\n", encoding="utf-8")

    print(f"Generated {len(expected_files)} WebP frames for {len(manifest)} exercises.")
    print(f"Per-platform size: {total_bytes:,} bytes ({total_bytes / (1024 * 1024):.2f} MB), max frame: {max_frame_bytes:,} bytes, re-encoded lower: {reencoded_count}")
    if total_bytes > HARD_CAP_TOTAL_BYTES:
        print(f"ERROR: Total media size {total_bytes:,} exceeds 18 MB cap ({HARD_CAP_TOTAL_BYTES:,})", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
