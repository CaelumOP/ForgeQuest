#!/usr/bin/env python3
"""
Generates tools/catalog/wp1c_exercises.py from free-exercise-db for all unmapped exercises
in categories: strength, stretching, plyometrics, powerlifting, cardio
(skipping strongman and olympic weightlifting, and excluding near-duplicates in wp1c_merges.py).
"""
from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent
FEDB_ROOT = Path(os.environ.get("FEDB_ROOT", "D:/Claude/tmp/fedb/free-exercise-db-main"))

MUSCLE_MAP: dict[str, str] = {
    "abdominals": "ABS",
    "abductors": "GLUTES",
    "adductors": "ADDUCTORS",
    "biceps": "BICEPS",
    "calves": "CALVES",
    "chest": "CHEST",
    "forearms": "FOREARMS",
    "glutes": "GLUTES",
    "hamstrings": "HAMSTRINGS",
    "lats": "UPPER_BACK",
    "lower back": "LOWER_BACK",
    "middle back": "UPPER_BACK",
    "neck": "TRAPS",
    "quadriceps": "QUADS",
    "shoulders": "SHOULDERS",
    "traps": "TRAPS",
    "triceps": "TRICEPS",
}

MUSCLE_AR: dict[str, str] = {
    "CHEST": "الصدر",
    "SHOULDERS": "الأكتاف",
    "BICEPS": "البايسبس",
    "TRICEPS": "الترايسبس",
    "FOREARMS": "السواعد",
    "ABS": "البطن",
    "OBLIQUES": "الخواصر",
    "TRAPS": "الترابيس",
    "UPPER_BACK": "الظهر",
    "LOWER_BACK": "أسفل الظهر",
    "GLUTES": "الأرداف",
    "QUADS": "الأماميات",
    "HAMSTRINGS": "الخلفيات",
    "ADDUCTORS": "العضلة الضامة",
    "CALVES": "البطات",
    "TIBIALIS": "مقدمة الساق",
}


def to_snake_id(fedb_id: str) -> str:
    s = fedb_id.lower()
    s = re.sub(r"[^a-z0-9]+", "_", s).strip("_")
    return s


def map_muscles(prim_raw: list[str], sec_raw: list[str]) -> tuple[list[str], list[str]]:
    prim: list[str] = []
    for m in prim_raw:
        mapped = MUSCLE_MAP.get(m.lower())
        if mapped and mapped not in prim:
            prim.append(mapped)
    if not prim:
        prim = ["ABS"]
    sec: list[str] = []
    for m in sec_raw:
        mapped = MUSCLE_MAP.get(m.lower())
        if mapped and mapped not in prim and mapped not in sec:
            sec.append(mapped)
    return prim, sec


def map_equipment(entry: dict) -> str:
    eq = (entry.get("equipment") or "").lower()
    nl = entry["name"].lower()
    cat = entry["category"]
    if cat == "stretching" and eq in ("", "body only", "other", "foam roll"):
        return "BODYWEIGHT"
    if eq == "barbell" or eq == "e-z curl bar":
        if "bench" in nl and "incline" in nl or "decline" in nl:
            return "BARBELL,ADJ_BENCH"
        if "bench press" in nl or "floor press" not in nl and "bench" in nl:
            return "BARBELL,FLAT_BENCH"
        if "squat" in nl or "rack" in nl or "pin press" in nl or "good morning" in nl:
            return "BARBELL,SQUAT_RACK"
        return "BARBELL"
    if eq == "dumbbell":
        if "incline" in nl or "decline" in nl:
            return "DUMBBELLS,ADJ_BENCH"
        if "bench" in nl or "lying" in nl:
            return "DUMBBELLS,FLAT_BENCH"
        return "DUMBBELLS"
    if eq == "kettlebells":
        return "KETTLEBELL"
    if eq == "cable":
        if "pulldown" in nl or "lat" in nl:
            return "LAT_PULLDOWN"
        if "seated" in nl and "row" in nl:
            return "SEATED_ROW_MACHINE"
        return "CABLE_MACHINE"
    if eq == "bands":
        return "BAND"
    if eq == "machine":
        if "smith" in nl:
            return "SMITH_MACHINE"
        if "leg press" in nl:
            return "LEG_PRESS"
        if "hack squat" in nl:
            return "HACK_SQUAT"
        if "leg extension" in nl:
            return "LEG_EXTENSION"
        if "leg curl" in nl or "hamstring" in nl:
            return "LEG_CURL"
        if "calf" in nl:
            return "CALF_MACHINE"
        if "chest press" in nl:
            return "CHEST_PRESS_MACHINE"
        if "shoulder press" in nl:
            return "SHOULDER_PRESS_MACHINE"
        if "pec" in nl or "fly" in nl or "butterfly" in nl:
            return "PEC_DECK"
        if "pulldown" in nl:
            return "LAT_PULLDOWN"
        if "row" in nl:
            return "SEATED_ROW_MACHINE"
        if "preacher" in nl or "bicep" in nl:
            return "PREACHER_CURL_MACHINE"
        if "treadmill" in nl or "bike" in nl or "bicycling" in nl or "elliptical" in nl or "rowing" in nl or "stair" in nl or "step mill" in nl:
            return "BODYWEIGHT"
        return "MACHINE"
    if eq in ("body only", "", "medicine ball", "exercise ball", "foam roll", "other"):
        if "plate" in nl:
            return "BARBELL"
        if "pull-up" in nl or "pullup" in nl or "chin-up" in nl or "chinup" in nl or "hanging" in nl:
            return "PULL_UP_BAR"
        if "dip" in nl and "bench" not in nl:
            return "DIP_STATION"
        if "hyperextension" in nl or "roman" in nl:
            return "ROMAN_CHAIR"
        if "bench" in nl and cat not in ("plyometrics", "cardio"):
            return "FLAT_BENCH"
        return "BODYWEIGHT"
    return "BODYWEIGHT"


def map_kind(entry: dict) -> str:
    cat = entry["category"]
    nl = entry["name"].lower()
    eq = (entry.get("equipment") or "").lower()
    force = (entry.get("force") or "").lower()
    if cat == "stretching":
        return "HOLD"
    if any(k in nl for k in ("plank", "isometric", "hold", "wall sit", "bridge hold", "l-sit", "static")):
        return "HOLD"
    if force == "static" and eq in ("", "body only", "other", "foam roll"):
        return "HOLD"
    if eq in ("barbell", "dumbbell", "kettlebells", "cable", "machine", "e-z curl bar"):
        if cat == "cardio" and eq == "machine":
            return "HOLD"
        return "WEIGHTED"
    if cat == "cardio":
        return "HOLD" if any(k in nl for k in ("treadmill", "bicycling", "bike", "elliptical", "rowing", "stair", "step mill", "running", "skating")) else "REPS"
    return "REPS"


def map_pattern(entry: dict, prim: list[str]) -> str:
    cat = entry["category"]
    nl = entry["name"].lower()
    p0 = prim[0]
    if cat == "stretching":
        return "MOBILITY"
    if cat in ("cardio", "plyometrics"):
        if "push" in nl:
            return "HORIZONTAL_PUSH"
        if "throw" in nl or "slam" in nl:
            return "CORE_FLEXION_ROTATION"
        return "CARRY_CONDITIONING"
    if "lateral raise" in nl or "side raise" in nl:
        return "LATERAL_RAISE"
    if "face pull" in nl or "rear delt" in nl or "reverse fly" in nl or "shrug" in nl:
        return "UPPER_BACK_PULL"
    if "pulldown" in nl or "pull-up" in nl or "pullup" in nl or "chin-up" in nl or "chinup" in nl or "pullover" in nl:
        return "VERTICAL_PULL"
    if "row" in nl:
        return "HORIZONTAL_PULL"
    if "dip" in nl or "decline" in nl:
        return "DIP_PUSH"
    if "bench press" in nl or "chest press" in nl or "push-up" in nl or "pushup" in nl or "fly" in nl or "floor press" in nl or "board press" in nl or "pin press" in nl:
        return "HORIZONTAL_PUSH"
    if "shoulder press" in nl or "overhead press" in nl or "military press" in nl or "arnold" in nl or "front raise" in nl or "upright row" in nl or "jerk" in nl or "push press" in nl or "clean and press" in nl:
        return "VERTICAL_PUSH"
    if "curl" in nl and p0 in ("BICEPS", "FOREARMS"):
        return "ELBOW_FLEXION"
    if "wrist" in nl or "plate pinch" in nl or "grip" in nl:
        return "ELBOW_FLEXION"
    if "tricep" in nl or "pushdown" in nl or "kickback" in nl or "skull" in nl or "french press" in nl or "jm press" in nl or "tate press" in nl:
        return "ELBOW_EXTENSION"
    if "lunge" in nl or "split squat" in nl or "step-up" in nl or "step up" in nl or "single-leg" in nl or "one-leg" in nl or "pistol" in nl:
        return "SQUAT_UNILATERAL"
    if "squat" in nl or "leg press" in nl or "leg extension" in nl or "sissy" in nl:
        return "SQUAT_BILATERAL"
    if "leg curl" in nl or "hamstring curl" in nl or "nordic" in nl:
        return "KNEE_FLEXION"
    if "deadlift" in nl or "good morning" in nl or "rack pull" in nl or "hip thrust" in nl or "glute bridge" in nl or "hyperextension" in nl or "back extension" in nl or "pull through" in nl or "swing" in nl or "clean" in nl or "snatch" in nl:
        return "HIP_HINGE"
    if "calf" in nl or "ankle" in nl or "toe raise" in nl:
        return "ANKLE_FLEXION"
    if "adduct" in nl or "groin" in nl:
        return "HIP_ADDUCTION"
    if "plank" in nl or "rollout" in nl or "ab wheel" in nl or "hollow" in nl or "bridge" in nl:
        return "CORE_ANTI_EXTENSION"
    if "crunch" in nl or "sit-up" in nl or "situp" in nl or "twist" in nl or "leg raise" in nl or "knee raise" in nl or "flutter" in nl or "jackknife" in nl or "v-up" in nl or "side bend" in nl or "wood chop" in nl:
        return "CORE_FLEXION_ROTATION"
    # Fallback by primary muscle
    fallback = {
        "CHEST": "HORIZONTAL_PUSH",
        "SHOULDERS": "VERTICAL_PUSH",
        "BICEPS": "ELBOW_FLEXION",
        "TRICEPS": "ELBOW_EXTENSION",
        "FOREARMS": "ELBOW_FLEXION",
        "ABS": "CORE_FLEXION_ROTATION",
        "OBLIQUES": "CORE_FLEXION_ROTATION",
        "TRAPS": "UPPER_BACK_PULL",
        "UPPER_BACK": "HORIZONTAL_PULL",
        "LOWER_BACK": "HIP_HINGE",
        "GLUTES": "HIP_HINGE",
        "QUADS": "SQUAT_BILATERAL",
        "HAMSTRINGS": "HIP_HINGE",
        "ADDUCTORS": "HIP_ADDUCTION",
        "CALVES": "ANKLE_FLEXION",
        "TIBIALIS": "ANKLE_FLEXION",
    }
    return fallback.get(p0, "CARRY_CONDITIONING")


# Ordered phrase replacements for natural Saudi/Gulf gym Arabic names
PHRASE_RULES: list[tuple[str, str]] = [
    ("bench press", "بنش برس"),
    ("chest press", "شيست برس"),
    ("floor press", "فلور برس"),
    ("board press", "بورد برس"),
    ("pin presses", "بين برس"),
    ("shoulder press", "شولدر برس"),
    ("military press", "شولدر برس"),
    ("overhead press", "شولدر برس"),
    ("push press", "بوش برس"),
    ("leg press", "ليق برس"),
    ("french press", "فرينش برس"),
    ("jm press", "جي إم برس"),
    ("tate press", "تيت برس"),
    ("arnold press", "أرنولد برس"),
    ("lat pulldown", "لات بول داون"),
    ("pulldown", "بول داون"),
    ("pull-up", "عقلة"),
    ("pullup", "عقلة"),
    ("chin-up", "عقلة عكسية"),
    ("chinup", "عقلة عكسية"),
    ("push-up", "ضغط (بوش أب)"),
    ("pushup", "ضغط (بوش أب)"),
    ("romanian deadlift", "رومانيان ديدلفت"),
    ("sumo deadlift", "سومو ديدلفت"),
    ("stiff-legged deadlift", "ديدلفت بأرجل مستقيمة"),
    ("deadlift", "ديدلفت"),
    ("rack pull", "راك بول"),
    ("good morning", "قود مورنينق"),
    ("box squat", "بوكس سكوات"),
    ("hack squat", "هاك سكوات"),
    ("front squat", "فرونت سكوات"),
    ("goblet squat", "قوبلت سكوات"),
    ("split squat", "سبليت سكوات"),
    ("zercher squat", "زيرشر سكوات"),
    ("squat", "سكوات"),
    ("leg extension", "ليق إكستنشن"),
    ("leg curl", "ليق كيرل"),
    ("hamstring curl", "ليق كيرل للخلفيات"),
    ("preacher curl", "لاري سكوت"),
    ("concentration curl", "كيرل تركيز"),
    ("hammer curl", "هامر كيرل"),
    ("spider curl", "سبايدر كيرل"),
    ("reverse curl", "كيرل عكسي"),
    ("wrist curl", "كيرل سواعد"),
    ("bicep curl", "بايسبس كيرل"),
    ("biceps curl", "بايسبس كيرل"),
    ("curl", "كيرل"),
    ("triceps extension", "تمديد الترايسبس"),
    ("tricep extension", "تمديد الترايسبس"),
    ("triceps pushdown", "تراي بوش داون"),
    ("pushdown", "بوش داون"),
    ("kickback", "كيك باك"),
    ("lateral raise", "رفرفة جانبية"),
    ("front raise", "رفرفة أمامية"),
    ("rear delt raise", "رفرفة كتف خلفي"),
    ("calf raise", "تمرين بطات"),
    ("leg raise", "رفع الأرجل"),
    ("knee raise", "رفع الركبتين"),
    ("hip thrust", "هيب ثرست"),
    ("glute bridge", "جسر الورك"),
    ("hyperextension", "هايبر إكستنشن"),
    ("back extension", "تمرين أسفل الظهر"),
    ("upright row", "سحب علوي للكتف"),
    ("face pull", "فيس بول"),
    ("bent over row", "سحب ظهر منحني"),
    ("bent-over row", "سحب ظهر منحني"),
    ("seated row", "سحب ظهر جالس"),
    ("row", "سحب ظهر"),
    ("shrug", "شراقز (ترابيس)"),
    ("flyes", "تفتيح"),
    ("flye", "تفتيح"),
    ("fly", "تفتيح"),
    ("crossover", "تقاطع"),
    ("pullover", "بول أوفر"),
    ("pull through", "بول ثرو"),
    ("lunge", "لانجز (طعن)"),
    ("step-up", "صعود الصندوق"),
    ("crunch", "كرانش"),
    ("sit-up", "تمرين بطن"),
    ("plank", "بلانك"),
    ("russian twist", "تويست روسي"),
    ("wood chop", "وود تشوب"),
    ("ab rollout", "عجلة البطن"),
    ("rollout", "تمديد البطن"),
    ("turkish get-up", "تيركش قت أب"),
    ("swing", "سوينق"),
    ("snatch", "سناتش"),
    ("clean", "كلين"),
    ("jerk", "جيرك"),
    ("dip", "متوازي (ديبس)"),
    ("stretch", "تمدد"),
    ("foam roll", "فوم رول"),
    ("smr", "فوم رول"),
    ("box jump", "قفز على الصندوق"),
    ("depth jump", "قفز عميق"),
    ("jump", "قفز"),
    ("hop", "حجل"),
    ("throw", "رمي"),
    ("slam", "ضرب الكرة بالأرض"),
    ("sprint", "سباق سرعة"),
    ("treadmill", "جهاز السير"),
    ("elliptical", "جهاز الإليبتيكال"),
    ("bicycling", "دراجة"),
    ("bike", "دراجة"),
    ("stairmaster", "جهاز الدرج"),
    ("step mill", "جهاز الدرج"),
    ("running", "جري"),
    ("jogging", "هرولة"),
    ("walking", "مشي"),
    ("skating", "تزلج"),
]

MODIFIER_AR: dict[str, str] = {
    "barbell": "بالبار",
    "dumbbell": "بالدمبل",
    "kettlebell": "بالكيتل بيل",
    "cable": "بالكيبل",
    "smith machine": "على جهاز سميث",
    "machine": "بالجهاز",
    "ez-bar": "ببار الزقزاق",
    "e-z curl bar": "ببار الزقزاق",
    "band": "بحبل المقاومة",
    "bands": "بحبل المقاومة",
    "medicine ball": "بالكرة الطبية",
    "exercise ball": "على كرة التوازن",
    "stability ball": "على كرة التوازن",
    "chains": "بالسلاسل",
    "incline": "مائل للأعلى",
    "decline": "مائل للأسفل",
    "seated": "جالسًا",
    "standing": "واقفًا",
    "lying": "مستلقيًا",
    "kneeling": "على الركبتين",
    "one-arm": "بذراع واحدة",
    "single-arm": "بذراع واحدة",
    "single-leg": "بساق واحدة",
    "one-leg": "بساق واحدة",
    "two-arm": "بكلتا الذراعين",
    "alternating": "بالتبادل",
    "alternate": "بالتبادل",
    "wide-grip": "قبضة واسعة",
    "close-grip": "قبضة ضيقة",
    "reverse-grip": "قبضة عكسية",
    "neutral-grip": "قبضة محايدة",
    "underhand": "قبضة عكسية",
    "behind the neck": "خلف الرقبة",
    "overhead": "فوق الرأس",
    "reverse": "عكسي",
    "lateral": "جانبي",
    "bent-over": "مع الانحناء",
    "weighted": "بوزن إضافي",
    "bodyweight": "بوزن الجسم",
}


def build_arabic_name(entry: dict, prim: list[str]) -> str:
    nl = entry["name"].lower()
    cat = entry["category"]

    if cat == "stretching":
        muscle_lbl = MUSCLE_AR.get(prim[0], "العضلات")
        if "foam roll" in nl or "smr" in nl or "myofascial" in nl:
            return f"فوم رول: {muscle_lbl}"
        pos = ""
        if "seated" in nl:
            pos = " جالسًا"
        elif "standing" in nl:
            pos = " واقفًا"
        elif "lying" in nl or "supine" in nl:
            pos = " مستلقيًا"
        elif "kneeling" in nl:
            pos = " على الركبتين"
        return f"تمدد {muscle_lbl}{pos}"

    core = ""
    for eng, ar in PHRASE_RULES:
        if eng in nl:
            core = ar
            break
    if not core:
        muscle_lbl = MUSCLE_AR.get(prim[0], "الجسم")
        if cat == "plyometrics":
            core = f"تمرين بليومتري ({muscle_lbl})"
        elif cat == "cardio":
            core = f"كارديو ({muscle_lbl})"
        else:
            core = f"تمرين {muscle_lbl}"

    mods: list[str] = []
    for k in ("smith machine", "barbell", "dumbbell", "kettlebell", "cable", "ez-bar", "bands", "band", "medicine ball", "exercise ball", "chains", "incline", "decline", "seated", "standing", "lying", "kneeling", "one-arm", "single-arm", "single-leg", "wide-grip", "close-grip", "reverse-grip", "neutral-grip", "overhead", "alternating"):
        if k in nl:
            ar_m = MODIFIER_AR[k]
            if ar_m not in core and ar_m not in mods:
                mods.append(ar_m)
            if len(mods) >= 3:
                break

    if not mods:
        return core
    return f"{core} {' '.join(mods)}"


def build_cues(entry: dict, pattern: str, cat: str) -> tuple[tuple[str, str, str], tuple[str, str, str]]:
    inst = [s.strip() for s in (entry.get("instructions") or []) if s and s.strip()]
    c1 = inst[0] if len(inst) >= 1 else "Set your posture and brace your core before starting."
    c2 = inst[1] if len(inst) >= 2 else "Move through a controlled full range of motion."
    # Keep cues concise (max ~140 chars)
    if len(c1) > 140:
        c1 = c1[:137].rsplit(" ", 1)[0] + "."
    if len(c2) > 140:
        c2 = c2[:137].rsplit(" ", 1)[0] + "."

    mistake_en_by_pat = {
        "MOBILITY": "Forcing the stretch into sharp pain or bouncing.",
        "CARRY_CONDITIONING": "Losing posture or landing stiffly with locked knees.",
        "HORIZONTAL_PUSH": "Flaring elbows too wide or bouncing at the bottom.",
        "DIP_PUSH": "Shrugging shoulders up toward your ears.",
        "VERTICAL_PUSH": "Arching the lower back excessively instead of bracing.",
        "LATERAL_RAISE": "Swinging the torso to lift with momentum.",
        "VERTICAL_PULL": "Pulling with momentum or stopping short of full range.",
        "HORIZONTAL_PULL": "Rounding the upper back or jerking the weight.",
        "UPPER_BACK_PULL": "Using heavy momentum instead of squeezing the upper back.",
        "SQUAT_BILATERAL": "Letting knees cave inward or heels lift off the floor.",
        "SQUAT_UNILATERAL": "Letting the front knee collapse inward.",
        "HIP_HINGE": "Rounding the lower back under load.",
        "KNEE_FLEXION": "Lifting hips off the pad to finish the rep.",
        "ELBOW_FLEXION": "Swinging the elbows forward to heave the weight.",
        "ELBOW_EXTENSION": "Letting elbows flare or drift during the press.",
        "CORE_ANTI_EXTENSION": "Letting the lower back sag toward the floor.",
        "CORE_FLEXION_ROTATION": "Pulling on the neck or using hip momentum.",
        "ANKLE_FLEXION": "Bouncing quickly through the bottom without a pause.",
        "HIP_ADDUCTION": "Using sudden momentum instead of steady control.",
    }
    cm = mistake_en_by_pat.get(pattern, "Rushing the movement with poor posture.")

    if cat == "stretching":
        a1 = "اتخذ الوضعية المريحة وثبّت جسمك دون شدّ زائد."
        a2 = "تنفّس بعمق واثبت عند نقطة الشدّ المريح دون ألم."
        am = "الضغط بقوة حتى الشعور بألم حاد أو الارتداد المفاجئ."
    elif cat == "plyometrics":
        a1 = "جهّز وقفتك واثنِ الركبتين قليلًا قبل الانطلاق."
        a2 = "انفجر بالحركة بسرعة واهبط بنعومة مع امتصاص الصدمة."
        am = "الهبوط بساقين مفرودتين أو فقدان توازن الجذع."
    elif cat == "cardio":
        a1 = "ابدأ بإيقاع ثابت مع استقامة الظهر واسترخاء الكتفين."
        a2 = "حافظ على انتظام التنفس وتدرّج في الشدة حسب مستواك."
        am = "الانحناء للأمام أو المبالغة في السرعة دون إحماء."
    else:
        cues_ar_by_pat = {
            "HORIZONTAL_PUSH": ("ثبّت لوحي الكتف للخلف والأسفل قبل البدء.", "تحكّم بالنزول ثم ادفع بثبات دون فقدان التوازن.", "فتح المرفقين بشكل مفرط أو الارتداد بالوزن."),
            "DIP_PUSH": ("أنزل الكتفين للأسفل وشدّ الجذع قبل النزول.", "انزل بمدى حركي مريح ثم ادفع للأعلى بثبات.", "رفع الكتفين نحو الأذنين أو التمايل."),
            "VERTICAL_PUSH": ("شدّ البطن والأرداف لحماية أسفل الظهر.", "ادفع للأعلى بسلاسة وتحكّم في النزول.", "تقويس أسفل الظهر بشكل مبالغ فيه أثناء الدفع."),
            "LATERAL_RAISE": ("أمل الجذع قليلًا مع ثني خفيف في المرفقين.", "ارفع بمستوى الكتفين وتحكّم بالنزول ببطء.", "تأرجح الجسم لاستخدام الزخم بدل الكتف."),
            "VERTICAL_PULL": ("اسحب الكتفين للأسفل أولًا ثم اسحب بالمرفقين.", "أكمل السحب بتحكّم ثم عد للأعلى ببطء.", "استخدام الزخم أو عدم إكمال المدى الحركي."),
            "HORIZONTAL_PULL": ("ثبّت الظهر مستقيمًا واسحب المرفقين للخلف.", "اعصر عضلات الظهر عند القمة ثم عد بتحكّم.", "تقويس الظهر أو نتع الوزن بسرعة."),
            "UPPER_BACK_PULL": ("وجّه السحب نحو أعلى الظهر مع ثبات الجذع.", "توقف لحظة عند الانقباض ثم عد ببطء.", "سحب الوزن بالذراعين فقط دون تفعيل أعلى الظهر."),
            "SQUAT_BILATERAL": ("ثبّت القدمين وشدّ الجذع قبل النزول.", "انزل بتحكّم مع محاذاة الركبتين ثم ادفع للأعلى.", "دخول الركبتين للداخل أو رفع الكعبين عن الأرض."),
            "SQUAT_UNILATERAL": ("وزّع التوازن على القدم الأمامية مع استقامة الجذع.", "انزل عموديًا بتحكّم ثم ادفع من منتصف القدم.", "انهيار الركبة للداخل أو فقدان التوازن."),
            "HIP_HINGE": ("حافظ على استقامة الظهر وادفع الورك للخلف.", "انزل حتى تشعر بشدّ الخلفيات ثم ارجع بالورك.", "تقويس أسفل الظهر تحت الحمل."),
            "KNEE_FLEXION": ("ثبّت الورك على المقعد قبل بدء الثني.", "اسحب بتحكّم حتى أقصى انقباض ثم عد ببطء.", "رفع الورك عن المقعد للمساعدة في السحب."),
            "ELBOW_FLEXION": ("ثبّت المرفقين بجانب الجذع دون حركة.", "ارفع الوزن بتحكّم واعصر العضلة ثم أنزله ببطء.", "مرجحة المرفقين أو الظهر لرفع الوزن."),
            "ELBOW_EXTENSION": ("ثبّت العضدين وقفل وضع المرفقين.", "مدّ الساعدين بالكامل ثم عد بتحكّم دون اندفاع.", "فتح المرفقين أو تحريك الكتفين أثناء المدّ."),
            "CORE_ANTI_EXTENSION": ("شدّ عضلات البطن والأرداف لمنع تقوّس الظهر.", "تنفّس بهدوء وحافظ على استقامة الجسم.", "هبوط أسفل الظهر نحو الأرض."),
            "CORE_FLEXION_ROTATION": ("ركز على انقباض عضلات البطن مع كل عدّة.", "تحرّك بتحكّم دون شدّ الرقبة باليدين.", "استخدام زخم الورك أو شدّ الرقبة للأمام."),
            "ANKLE_FLEXION": ("قف بثبات مع مدّ كامل للكاحل عند الأسفل.", "ارفع على أطراف الأصابع وتوقف لحظة عند القمة.", "الارتداد السريع في الأسفل دون توقف."),
            "HIP_ADDUCTION": ("تحكّم في المدى الحركي دون استعجال.", "اضغط للداخل بثبات ثم عد ببطء.", "استخدام الزخم السريع بدل التحكم العضلي."),
        }
        a1, a2, am = cues_ar_by_pat.get(pattern, ("اتخذ وضعية ثابتة وشدّ الجذع قبل البدء.", "نفّذ الحركة بمدى كامل وتحكّم في النزول.", "الاستعجال أو فقدان استقامة الظهر."))

    return (c1, c2, cm), (a1, a2, am)


def main() -> int:
    sys.path.insert(0, str(TOOLS_DIR))
    from existing_overrides import EXISTING_OVERRIDES
    from new_exercises import NEW_EXERCISES
    from wp1c_merges import SKIPPED_CATEGORIES, WP1C_MERGES

    base_186 = json.loads((TOOLS_DIR / "base_186.json").read_text(encoding="utf-8"))
    used_ids: set[str] = {b["id"] for b in base_186} | {e["id"] for e in NEW_EXERCISES}
    mapped_fedb: set[str] = {v[2] for v in EXISTING_OVERRIDES.values() if v[2]} | {e["fedb"] for e in NEW_EXERCISES}

    fedb_list: list[dict] = json.loads((FEDB_ROOT / "dist/exercises.json").read_text(encoding="utf-8"))

    out_items: list[dict] = []
    skipped_count = 0
    merged_count = 0

    for entry in fedb_list:
        fid = entry["id"]
        cat = entry["category"]
        if fid in mapped_fedb:
            continue
        if cat in SKIPPED_CATEGORIES:
            skipped_count += 1
            continue
        if fid in WP1C_MERGES:
            merged_count += 1
            continue

        eid = to_snake_id(fid)
        if eid in used_ids:
            # Disambiguate cleanly if an existing id already took that snake_case name
            eid = f"{eid}_fedb"
        assert eid not in used_ids, f"Collision on {eid}"
        used_ids.add(eid)

        prim, sec = map_muscles(entry.get("primaryMuscles") or [], entry.get("secondaryMuscles") or [])
        equipment = map_equipment(entry)
        kind = map_kind(entry)
        pattern = map_pattern(entry, prim)
        is_stretch = cat == "stretching"
        ar_name = build_arabic_name(entry, prim)
        cues_en, cues_ar = build_cues(entry, pattern, cat)
        has_media = len(entry.get("images") or []) >= 2

        aliases = [entry["name"].lower(), MUSCLE_AR[prim[0]]]
        if is_stretch:
            aliases.extend(["stretch", "تمدد", "إطالة"])
        elif cat == "cardio":
            aliases.extend(["cardio", "كارديو", "لياقة"])
        elif cat == "plyometrics":
            aliases.extend(["plyo", "بليومتري", "قفز"])

        out_items.append({
            "id": eid,
            "name": entry["name"],
            "ar": ar_name,
            "kind": kind,
            "primary": prim,
            "secondary": sec,
            "equipment": equipment,
            "pattern": pattern,
            "stretch": is_stretch,
            "aliases": aliases,
            "cues_en": cues_en,
            "cues_ar": cues_ar,
            "fedb": fid,
            "has_media": has_media,
        })

    # Verify all merge targets exist
    all_target_ids = used_ids
    for fid, target_id in WP1C_MERGES.items():
        assert target_id in all_target_ids, f"Merge target {target_id} for {fid} not found!"

    lines = [
        "# Auto-generated WP1c additions from yuhonas/free-exercise-db (The Unlicense / Public Domain).",
        "# Categories included: strength, stretching, plyometrics, powerlifting, cardio.",
        f"# Imported: {len(out_items)}, Merged: {merged_count}, Skipped (strongman/olympic): {skipped_count}.",
        "",
        "WP1C_EXERCISES = " + json.dumps(out_items, ensure_ascii=False, indent=2)
        .replace(": true", ": True")
        .replace(": false", ": False")
        .replace(": null", ": None"),
        "",
    ]
    (TOOLS_DIR / "wp1c_exercises.py").write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote wp1c_exercises.py: imported={len(out_items)}, merged={merged_count}, skipped={skipped_count}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
