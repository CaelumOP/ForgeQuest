"""Two weeks of made-up history on top of Sam's backup, for the returning-user round."""
import json, sys
from datetime import date, datetime, timedelta, timezone

SRC, OUT = sys.argv[1], sys.argv[2]
d = json.load(open(SRC, encoding="utf-8"))
TZ = timezone(timedelta(hours=3))
EPOCH = date(1970, 1, 1)
today = date(2026, 10, 25)
act = next(p["id"] for p in d["plans"] if p["active"])
days = {x["dayOfWeek"]: x for x in d["planDays"] if x["planId"] == act}
items = {}
for i in d["planItems"]:
    items.setdefault(i["dayId"], []).append(i)

sid = max(s["id"] for s in d["sessions"]) + 1
setid = max(s["id"] for s in d["sets"]) + 1
load = {"goblet_squat": 16.0, "db_bench_press": 14.0, "db_row": 16.0, "db_lunge": 10.0, "db_shoulder_press": 10.0, "hammer_curl": 10.0}
reps_bw = {"hip_bridge": 12, "knee_push_up": 12}

def ms(day, h, m=0):
    return int(datetime(day.year, day.month, day.day, h, m, tzinfo=TZ).timestamp() * 1000)

start = today - timedelta(days=13)  # Mon Oct 12
for n in range(13):
    day = start + timedelta(days=n)
    dow = day.isoweekday()
    pd = days.get(dow)
    if not pd or pd["isRest"]:
        continue
    ed = (day - EPOCH).days
    fri = dow == 5
    d["sessions"].append({"id": sid, "planDayId": pd["id"], "title": pd["title"], "epochDay": ed,
                          "startedAt": ms(day, 18), "finishedAt": ms(day, 18, 46), "note": "", "debrief": ""})
    for pos, it in enumerate(sorted(items[pd["id"]], key=lambda x: x["position"])):
        ex = it["exerciseId"]
        for k in range(it["sets"]):
            if ex in load:
                reps, w = 10, load[ex]
            else:
                # The bodyweight move climbs to 16 on the last two Fridays: "ready for a harder version".
                reps, w = (16 if ex == "knee_push_up" and fri and day > start + timedelta(days=2) else reps_bw[ex]), None
            d["sets"].append({"id": setid, "sessionId": sid, "exerciseId": ex, "itemPosition": pos, "block": it["block"],
                              "circuit": it["circuit"], "setIndex": k, "targetLabel": f"{it['repsMin']} reps", "reps": reps,
                              "seconds": None, "weightKg": w, "done": True, "isPr": False, "restSeconds": it["restSeconds"],
                              "note": "", "effort": "OK"})
            setid += 1
        if ex in load:
            load[ex] += 1.0
        elif ex in reps_bw and not fri:
            reps_bw[ex] += 1
    sid += 1

mid = 1
for n in range(14):
    day = start + timedelta(days=n)
    ed = (day - EPOCH).days
    for cat, name, kcal, prot, h in (("BREAKFAST", "Oats with milk", 420, 18, 8), ("LUNCH", "Chicken and rice", 650, 45, 13),
                                     ("DINNER", "Beef wrap", 700, 38, 20), ("SNACKS", "Greek yogurt", 150, 15, 16)):
        if cat == "SNACKS" and n % 3 == 0:
            continue
        d["meals"].append({"id": mid, "epochDay": ed, "loggedAt": ms(day, h), "name": name, "category": cat, "kcal": kcal,
                           "protein": float(prot), "quantity": 1.0, "carbs": 0.0, "photoPath": None, "foodId": None})
        mid += 1
d["weights"] = [{"epochDay": (start + timedelta(days=n) - EPOCH).days, "kg": round(71.0 + n * 0.08, 1), "loggedAt": ms(start + timedelta(days=n), 7)}
                for n in range(14) if n % 2 == 0] + d["weights"]
d["createdAt"] = ms(today, 9)
json.dump(d, open(OUT, "w", encoding="utf-8"), ensure_ascii=False)
print(len(d["sessions"]), "sessions", len(d["sets"]), "sets", len(d["meals"]), "meals", len(d["weights"]), "weights")
