# Turn a YouTube training programme into a ForgeQuest plan

Paste everything below the line into the Gemini app (Pro, with the YouTube playlist or video links), then copy the JSON it
returns into ForgeQuest → Workouts → Edit plan → Paste a plan. Fill in the two placeholders first.

---

You are converting a calisthenics programme from YouTube into a plan file for my workout app. Watch every video I link
and work from what the coach actually does and says on screen, not from general knowledge.

PROGRAMME NAME: <e.g. Hemo · Beginner level>
VIDEOS (one per training day, in order): <paste the playlist link or one YouTube link per line>

What to produce: one JSON object, in a single ```json code block, nothing else before or after. Shape:

{"name": string, "description": string, "days": [
  {"dayOfWeek": 1-7, "title": string, "isRest": boolean, "note": string, "items": [
    {"exerciseId": string, "name": string, "kind": "REPS"|"HOLD"|"WEIGHTED", "muscles": [string],
     "block": string, "circuit": boolean, "sets": int, "repsMin": int|null, "repsMax": int|null,
     "seconds": int|null, "weightKg": number|null, "restSeconds": int, "note": string, "videoUrl": string}
  ]}
]}

Rules:
1. dayOfWeek: 1 = Monday … 7 = Sunday. Include all 7 days. Put the programme's training days in the order the coach
   gives them starting Monday; the days he says to rest get "isRest": true with an empty items list and a short note.
   Title training days like "Day 2 · Upper body".
2. Blocks: use "Warm-up", "Core", "Main", "Legs" exactly as the coach structures the session. Exercises done one after
   another and then repeated as rounds get "circuit": true, and "sets" is the number of rounds he says (3–4 → use 3).
   A warm-up done as straight sets (e.g. jumping jacks 10 × 5) gets "circuit": false and "sets": 5.
3. Reps vs holds: timed holds (planks, dead hang) use "kind": "HOLD" and "seconds"; everything else uses "repsMin" and
   "repsMax" (equal when he gives one number). "weightKg" is null unless he loads the movement. "restSeconds": 30 for
   circuit items, 60 for straight sets, unless he says otherwise.
4. exerciseId: snake_case, English, stable. Reuse these ids when the movement is the same (same stance, same grip):
   jumping_jacks plank_shoulder_taps burpee incline_hollow_plank incline_reverse_plank hip_bridge crunch superman_hold
   knee_scapular_push_up scapular_australian_pull_up bent_leg_bench_dip scapular_australian_chin_up knee_push_up
   australian_pull_up reverse_grip_bench_dip australian_chin_up wide_knee_push_up dead_hang incline_diamond_knee_push_up
   half_squat half_wide_squat half_narrow_squat calf_raise_turned_out calf_raise push_up diamond_push_up pike_push_up
   pull_up chin_up negative_pull_up dip squat lunge bulgarian_split_squat pistol_squat nordic_curl plank hollow_hold
   hanging_knee_raise hanging_leg_raise muscle_up l_sit handstand_hold handstand_push_up tuck_planche
   pseudo_planche_push_up tuck_front_lever front_lever_row back_lever_tuck weighted_pull_up weighted_chin_up weighted_dip
   weighted_push_up
   For any other movement invent a new snake_case id (e.g. "incline_pike_push_up") and fill "name" (short English name
   as the coach says it), "kind" and "muscles". Muscles come only from: CHEST SHOULDERS BICEPS TRICEPS FOREARMS ABS
   OBLIQUES TRAPS UPPER_BACK LOWER_BACK GLUTES QUADS HAMSTRINGS ADDUCTORS CALVES TIBIALIS. List the main muscle first,
   at most three.
5. videoUrl: only when I gave you video links above. Then, for every item, the link to the exact moment the coach first demonstrates that exercise, as
   https://www.youtube.com/watch?v=VIDEOID&t=SSSs where SSS is whole seconds from the start of that day's video.
   Take the timestamp from the on-screen label or the first clear demonstration, not from the intro. Be as precise as
   the video allows; if you are unsure between two moments, choose the earlier one. If I gave no links, leave videoUrl out entirely; never invent or search for a link.
6. "description": two sentences in the coach's own terms: how rounds and rest work, and when to move to the next level.
   "note" on an item only for a cue the coach stresses (e.g. "3 s hold at the top").
7. Do not add exercises he doesn't show, do not pad sets, and do not round his numbers. If something is unclear in the
   video, say so in a "note" rather than guessing.

Before the JSON, no explanation. After the JSON, add one short list of any timestamps you were unsure about so I can
check them.
