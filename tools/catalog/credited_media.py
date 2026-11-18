"""Openly licensed exercise images from sources other than free-exercise-db, with their credits.

Originals live in credited_src/. build_media.py encodes them into the same 2-frame WebP files as
the rest (resized, transparent backgrounds flattened to white, converted to WebP); build_catalog.py
gives the listed exercise ids their mediaKey. The apps show these credits on the About screen,
generated into ExerciseMediaCredits.kt and exercise-media-credits.ts.

Each item also carries a difficulty level (same scale as the dataset), since the apps expect
one for every exercise that has images.

CC BY-SA images stay under their licence after our changes (resize, WebP).
"""

EVERKINETIC = {
    "author": "Everkinetic",
    "license": "CC BY-SA 3.0",
    "license_url": "https://creativecommons.org/licenses/by-sa/3.0/",
    "source_url": "https://github.com/lczarnec/everkinetic_modifications",
}

CREDITED_MEDIA: list[dict] = [
    {
        "key": "burpee",
        "level": "intermediate",
        "ids": ["burpee"],
        "frames": ["Burpee_1_Neutral_Position.jpg", "Burpee_5_Thrust.jpg"],
        "title": "Burpee 1 Neutral Position; Burpee 5 Thrust (commons.wikimedia.org/wiki/File:Burpee_5_Thrust.jpg)",
        "author": "Taco fleur",
        "license": "CC BY-SA 4.0",
        "license_url": "https://creativecommons.org/licenses/by-sa/4.0/",
        "source_url": "https://commons.wikimedia.org/wiki/File:Burpee_1_Neutral_Position.jpg",
    },
    {
        "key": "l_sit",
        "level": "intermediate",
        "ids": ["l_sit"],
        "frames": ["West_Point_male_gymnast_L-sit.jpg", "West_Point_male_gymnast_L-sit.jpg"],
        "title": "West Point male gymnast L-sit",
        "author": "Tommy Gilligan / West Point Public Affairs",
        "license": "Public domain",
        "license_url": "",
        "source_url": "https://commons.wikimedia.org/wiki/File:West_Point_male_gymnast_L-sit.jpg",
    },
    {
        "key": "bicycle_crunch",
        "level": "beginner",
        "ids": ["bicycle_crunch"],
        "frames": ["everkinetic_cross_body_crunch_1.png", "everkinetic_cross_body_crunch_2.png"],
        "title": "Cross body crunch",
        **EVERKINETIC,
    },
    {
        "key": "band_curl",
        "level": "beginner",
        "ids": ["band_curl"],
        "frames": ["everkinetic_quick_alternating_bicep_curls_with_band_1.png",
                   "everkinetic_quick_alternating_bicep_curls_with_band_2.png"],
        "title": "Quick alternating bicep curls with band",
        **EVERKINETIC,
    },
    {
        "key": "lunge",
        "level": "beginner",
        "ids": ["lunge"],
        "frames": ["everkinetic_walking_lunges_1.png", "everkinetic_walking_lunges_2.png"],
        "title": "Walking lunges",
        **EVERKINETIC,
    },
    {
        "key": "calf_raise",
        "level": "beginner",
        "ids": ["calf_raise"],
        "frames": ["everkinetic_calf_raises_with_band_1.png", "everkinetic_calf_raises_with_band_2.png"],
        "title": "Calf raises with band",
        **EVERKINETIC,
    },
]

CREDITED_BY_ID: dict[str, str] = {i: m["key"] for m in CREDITED_MEDIA for i in m["ids"]}
