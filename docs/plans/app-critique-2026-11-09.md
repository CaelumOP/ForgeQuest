# ForgeQuest: full critique, 2026-11-09

Scope: the Android app on `feat/simple-home` including the uncommitted work. The web PWA was not reviewed.

How this was made: five read-only passes over the code and strings (finish screen, training content, AI, wording, structure). I did not run the app, so this judges what the code and text say, not how it feels in the hand. File and line references come from those passes; the ones a decision hangs on were re-checked, the rest should be confirmed before building. Arabic quality was not judged.

Items already in the handoff triage (live PR gold, tick-and-finish, warm-up counts, colour meaning and so on) are not repeated here.

---

## 1. The short version

The app's brain is much better than its voice. The maths underneath (progression, readiness, calorie check, the debrief logic) is careful and already deterministic. What the user actually reads and sees does not show that off: the best content is buried, a few sentences repeat daily, exercises have no instructions, and a large AI layer sits on top doing a worse job than the code beneath it.

Your instinct to shrink the AI and go deterministic is right, and you are closer to it than you think.

| Area | Score | One line |
|---|---|---|
| Deterministic engine (progression, readiness, nutrition, calorie check) | 8.5 | The real asset. Mostly invisible to the user. |
| Debrief logic | 8 | 13 situations, wins, watch-outs, next-time targets. Genuinely good. |
| Finish screen as a moment | 5.5 | Same layout every time; the good part is tenth from the top. |
| Onboarding | 7.5 | Two steps, fast. One real problem with default targets (4.6). |
| Food | 7 | Solid. Half its extras depend on AI. |
| Home | 6.5 | Up to 13 stacked elements; several ask for attention at once. |
| Settings | 6.5 | Well grouped now. The AI group is the biggest and least useful. |
| Wording | 6.5 | Calm and clean, not cheesy. Flat, few, and not specific enough. |
| Training plans | 5 | 4 starters, text that promises things the code does not do. |
| Exercise library | 4.5 | 100 exercises, zero instructions, 22 videos. |
| AI coach (value for its size) | 4 | About 2,500 lines and 60 strings for the weakest feature. |
| Gamification | 4 | XP, levels, ranks unlock nothing. Badges ignore gym users. |
| Code health | 7 | 29,440 lines, 39 test files, little dead code. |

Overall: about 6.5. The gap to 8 is content and presentation, not engineering.

---

## 2. AI: what to keep, what to cut

### What exists today
- Coach chat (Gemini, or Groq when a second key is set)
- Weekly AI check-in
- Coach "memory" that the model writes itself
- Plan text import (paste a plan, AI turns it into a plan)
- Meal photo scan, nutrition-label read, typed-food estimate
- Gemini Gem bridge: a Drive document written and read every 12 hours by a background worker
- Gemini meals inbox
- Model pickers (fast, smart, lite, coach, Groq)

AI-only files are about 1,150 lines. Add most of `CoachRepository.kt` (814) and `CoachScreen.kt` (585). "Gemini" is the second most common word in the app's strings (60 times), after "plan".

### The questions
- **Why is there a chat at all?** A chat makes the user think of a question. A coach tells you things before you ask. Your debrief already does that, with real numbers, and it cannot be wrong about them.
- **Why does the coach prompt ask the model to work out a deload and exact weights?** `Trends.kt` and `Progression.kt` already compute both. The prompt hands the model your numbers and asks it to redo the sums. A free-tier model will sometimes redo them wrongly, and then the app contradicts itself.
- **Why does the AI write to the user's data without asking?** `CoachRepository.kt:262-266` saves the model's "memory" line into coach notes and can change the user's equipment from it, with no confirmation. This is the one AI behaviour I would call a defect and not a taste question.
- **Why Groq?** A second provider, a second key, a second settings row and its own error strings, to speed up a feature you want to shrink.
- **Why five model settings?** Nobody choosing a fitness app wants to pick between flash, pro and flash-lite.
- **Why a 12-hour background worker for the bridge?** It runs on the phone for a feature very few people will set up.

### Recommendation: AI as an input tool, never as the coach
Keep AI only where code truly cannot do the job, which is turning messy input into structured data:
1. Meal photo to food entry
2. Nutrition label to food entry
3. Pasted plan text to a plan

All three end in a screen where the user confirms before anything is saved. That is the right shape.

Cut or park:
- Chat, weekly check-in, AI memory, Groq, the model pickers.
- The Gem bridge and its worker. The "copy a prompt, paste Gemini's reply" route you already chose in the handoff does the same job with no background work and no key.
- "Ask the coach about this workout" on the finish screen. It sends the user away from your best screen to your weakest.

What this buys: roughly 2,000 lines and 100+ strings (times two languages) removed, one settings group reduced to a few rows, and no way for the app to contradict its own numbers.

Care needed: the `gemini_bridge` worker name is on the project's never-delete list because it is scheduled on phones. Retiring it means cancelling that work by name on upgrade, not just deleting the class. The backup fields for AI settings must stay readable so old backups restore.

### The tab
The handoff already decided the fourth tab becomes Progress. Agreed, and I would go one step further: do not bring the chat back as a tab even with a key. Today's AI-off Coach tab is five thin cards (recovery, stalled lifts, a deload line, a week count, a Progress link). The insight engine in section 4 is what should fill that space.

One mismatch to fix on the way: the coach summary says lifts stall after "2+ weeks", `Trends.kt` says 3+.

---

## 3. The finish screen

### What is really there
You asked whether it is a template that will get boring. Half yes.

The **text** is better than you think. There are 13 situations (first ever, back after a break, rough day held, short session, PR day, mostly up, all steady, mixed, mostly down and more), three phrasings each, picked by rule. On top of that: up to 2 wins from 13 kinds, up to 2 watch-outs from 8 kinds, and up to 4 "next time" targets with real numbers. That is a real deterministic coach already.

The **screen** is what will get boring. Top to bottom, every single time:

1. Check icon
2. "Workout complete"
3. Workout title
4. Sets, Time, XP
5. Level card with a progress bar
6. PR and badge rows
7. Reminder row
8. Next up
9. Debrief card: title, score, reasons line, then finally the headline, wins, watch-outs and next-time lines
10. Done

### The questions
- **Why is the best content ninth?** The user sees a tick, a fixed phrase, three generic numbers and an XP bar before the one sentence written about *this* workout.
- **Why does a level bar sit above the debrief?** XP unlocks nothing (section 6). It is the least meaningful thing on the screen and it has the second-best position.
- **Why are Sets, Time and XP the hero numbers?** The user knows how many sets they did and roughly how long it took. None of the three says whether today was good.
- **Why does a new record look almost like a normal day?** A PR gets a small chip and a row. If there is one moment that should look different, it is this.
- **Why does the score start at 4?** It is 3 free points plus completion plus progress plus one for a PR, clamped to 4 to 10. So the real range is seven steps, most sessions will land on 7 to 9, and there is no word saying what the number means. Is a daily grade the right feeling at all? A steady week of 7s can read like "never good enough".
- **Why does the same footnote show every time?** "The app eases off by itself on a low-recovery day" appears on every finish for anyone with Health Connect on, not only on low-recovery days (`WorkoutDebriefMath.kt:185`).
- **Why only three phrasings for the common cases?** The variant is `sessionId mod 3`. "All steady" and "mostly up" are what a regular user gets most weeks, so each sentence comes round about every third workout. The rare cases (first ever, back after a break) have three phrasings that almost nobody will see twice.
- **Why is nothing about the bigger picture shown?** Streak, week count, lifetime totals, muscle groups trained, how today compares with a month ago: all computed elsewhere, none of it here.
- **Why is there nothing to share?** No summary card to send to a friend. That is the cheapest advertising a fitness app gets.

### What I would do
**Make the layout change with the day.** One rule: the top of the screen is whatever is most notable about this workout.

| Day type | Top of screen |
|---|---|
| PR day | The record itself, large: exercise, new number, old number |
| Milestone (10th, 50th, 100th workout, streak week 4/12/52, lifetime total crossed) | The milestone |
| Back after a break / first ever | The welcome line, no score |
| Rough day (low recovery, pain) | A quiet layout, no score, the "you managed it" line |
| Ordinary day | The headline sentence, then the single most interesting comparison |

Then the supporting lines (wins, watch-outs), then next time, then a small strip for XP and level at the bottom. Sets and time become one small line.

**Fix the rotation.** Give the four or five common situations 8 to 10 phrasings and the rare ones 2. Pick the least recently shown one (store the last few shown ids) so the same sentence cannot come back within a few weeks.

**Rethink the score.** Options, in my order of preference:
1. Replace the number with a word tier that has meaning ("Record day", "Step forward", "Held steady", "Light day").
2. Keep the number but show it only on days it is 8 or higher.
3. Keep as is and add the word next to it.

**One earned celebration.** No sound or confetti is right for the calm look. Keep it that way for ordinary days and spend the one strong haptic and one animation on a PR or milestone only, so it means something.

---

## 4. A deterministic insight engine (the answer to "rich comments without AI")

Yes, this is very doable, and it is the single highest-value thing to build. The debrief is already a small version of it.

### The shape
Each insight is a small record:
- **Trigger**: a rule over the user's data ("squat volume up 4 weeks in a row").
- **Rank**: how interesting it is. Rare things outrank common ones.
- **Cooldown**: how many workouts or days before the same insight may show again.
- **Phrasings**: 3 to 6 sentences with slots for real names and numbers.
- **Where**: finish screen, home, progress tab, reminder notification.

A picker gathers every insight whose trigger is true, drops the ones on cooldown, sorts by rank, and takes the top few for each place. Same data in, same text out: fully deterministic and unit-testable, like `WorkoutDebriefMath` today.

Why this stays fresh where a template does not: the *selection* changes, not only the wording. With 60 insight kinds and cooldowns, two consecutive finish screens rarely share a line.

### Insight kinds you can build from data you already store

**Records and near-records**
- New record (exists)
- One rep or 2.5 kg short of a record: "One more rep on pull-ups ties your best"
- First time at a round number: 10 pull-ups, 60 s hold, bodyweight bench
- Record in total reps for an exercise even though the best set did not move

**Comparisons over time**
- Today against four weeks ago for the same plan day
- Best month so far for an exercise
- Total volume this week against the average of the last four
- "Your push-ups have gone from 3×8 to 3×12 since September"

**Lifetime totals** (nothing in the app shows these today)
- Total workouts, sets, reps, hours trained, kg lifted
- Round-number crossings: 1,000th set, 100 hours, 100,000 kg
- Anniversaries: one month, six months, one year since the first workout

**Consistency**
- Same weekday five weeks running
- No missed planned day this month
- Weeks in a row at target, and how far to the next streak badge
- First workout back after N days (exists for 42+ days; a 10-day gap deserves a line too)

**Pace and rest** (you store `doneAt` on every set and never use it)
- Workout finished faster or slower than last time for the same work
- Average rest between sets, and whether it drifted from the plan
- Longest pause in the session
- Time of day: "Third morning workout this week"

**Balance**
- Sets per muscle group this week against the builder's own weekly targets
- A muscle that has had no direct work in 10 days
- Push to pull ratio over four weeks
- An exercise swapped out three times running: "Remove it from the plan?"

**Effort**
- Same numbers, felt easier (exists)
- Every set of an exercise marked hard two sessions running: hold, do not add
- A lift marked easy three sessions running without moving up

**Recovery and food crossed with training**
- Record set on a low-recovery day
- Protein goal hit on every training day this week
- Calories under target on three training days running, next to a drop in numbers
- Sleep under need for three nights and a heavy day planned tomorrow

**Technique tips tied to what just happened** (needs the cues from section 5)
- Squat marked hard: show one squat cue
- Pain on an exercise: show its commonest mistake and the easier version
- First time doing an exercise: show its two key cues before the first set

### Where else it pays off
- **Reminder notification.** Today it is one string: "Today: Push". It could be "Today: Push. Bench moves up to 62.5 kg" or "Two workouts left for week 6 of your streak". Same engine, no AI.
- **Readiness advice.** Five fixed sentences, and they are the most-read text in the app because the card is on Home every day. Tie them to the plan: "Legs today. Do the session, skip the extra sets."
- **Home "done" state.** "Done for today. Nice work." every day. Use the day's top insight instead.
- **Progress tab.** A short "This week" list of the top five insights is the content the Coach tab never had.

### Size of the job
About 60 insight kinds times 4 phrasings is 240 sentences per language. That is real writing work, and the Arabic needs proper plural forms, so keep each sentence to two or three slots. Build it in slices: the engine plus 15 kinds on the finish screen first, then notifications, then Home and Progress.

---

## 5. Training content

### Is the number of workouts enough?
Four starter plans is thin, but the number is not the main problem. Since the offline builder exists, starters are just shortcuts. The real problems are below.

**Text that promises what the code does not do**
- The builder's note says "Every 5th week, halve the sets" (`PlanBuilder.kt:57`). No code counts weeks. Either build the deload week or remove the sentence.
- The cool-down switch in the builder produces nothing: `COOL_DOWN_IDS` is an empty list (`PlanBuilder.kt:124`), because the catalogue has no stretches.
- The Hemo plan says "Stretch, then..." and has no stretch item. Its rest-day note says "Move to the Beginner level once this feels easy". There is no Beginner level plan.
- The bodyweight starter says "Needs little or no equipment" and includes Australian pull-ups, which need a bar.

**Gaps in the starters**
- Nothing between "3 days, 5 exercises" and "6-day gym split". The most common real-world plans are missing: 3-day gym full body, 4-day upper/lower.
- No follow-on from Hemo Foundation.
- No true zero-equipment plan.
- No 2-day minimum plan for busy weeks.

Suggested set of 8 to 10: keep the four, fix the bodyweight one, and add 3-day gym full body, 4-day upper/lower (gym), 4-day dumbbell, calisthenics level 2 (the missing "Beginner level"), and a 2-day minimal. Better still, define starters as saved builder answers so there is one engine to maintain, not hand-written lists.

**The builder**
- Ties are broken by lowest id, so the same answers always give the same plan, and "Rebuild" hands back an identical plan. Add a "give me a different version" option that rotates the choice among equally good candidates. Still deterministic.
- No change over weeks at all. Even a simple four-week cycle (three building weeks, one lighter) would make the plan feel alive and would make the "5th week" sentence true.
- No supersets, although fat-loss items already carry a `circuit` flag.
- No way to start a blank plan.

### The exercise library
100 exercises: 44 reps, 41 weighted, 15 holds.

- **Why does no exercise have instructions?** The model has id, name, kind, muscles, video link. No cues, no "common mistake", no image. Only 22 have a video; the other 78 open a YouTube search. For an app that is dropping its AI coach, this is the biggest content hole. Two cues and one common mistake per exercise is about 300 short lines per language, and it feeds the tip insights in section 4.
- **Why is equipment not a field?** It is inferred from ids and sections. Dumbbell and kettlebell are not separated. The builder and swap engine both depend on it.
- **Thin areas:** lower back 3, obliques 3, tibialis 3, calves 4, forearms 4, biceps 5, hamstrings 6. Cable and machine work is about 9 entries, which is thin for anyone on the gym plan.
- **Zero stretches or mobility drills.** 15 to 20 would make the cool-down switch real and give rest days something to offer.
- **Cardio is two entries** (jumping jacks, burpees). The builder's "cardio day" can only pick from four exercises. If people run, cycle or walk, there is nowhere to log it. Either add a simple duration-and-distance exercise kind or drop the cardio option. Health Connect already reads exercise sessions; showing those on the week view may be enough.

A reasonable target is 160 to 180 exercises: about 25 more gym movements (hamstrings, biceps, calves, cables, machines), 15 to 20 stretches, 10 cardio and conditioning, and a few more band and kettlebell moves.

Check before adding an exercise kind: how `ExerciseKind` is stored in Room and in backups, since the schema is at version 9 and old backups must still restore.

---

## 6. Gamification: why is this here?

- **XP, levels and ranks unlock nothing.** They are shown on Home, Profile and the finish screen, and change no feature. A bar that fills and does nothing stops being noticed in a couple of weeks.
- **XP is almost only attendance.** 50 per workout, 2 per set (capped), 25 per PR, 100 per badge. A long hard session and a short one are worth nearly the same.
- **Badges ignore gym users.** Of the 15 skill and strength badges, every one is calisthenics: muscle-up, pistol, L-sit, handstand, planche, front lever, pull-ups, dips. Someone on the Push-Pull-Legs gym plan can earn the attendance badges and nothing else. There is no badge for a bench, squat or deadlift milestone, for total volume, or for hours trained.
- **Three protein badges, none for calories, weigh-ins or logging days.**
- **"Snap & log" is a badge for using the AI scan.** If AI is off by default, this badge is unreachable for most users. Decide whether it stays.

Options:
1. **Make it mean something.** Levels unlock real things: plan variations, harder exercise tiers suggested, the share card styles, themes. Add badges for weighted lifts and lifetime totals.
2. **Shrink it.** Keep streak and badges (both are honest records of things done). Drop XP, level and rank, or reduce them to one line on Profile.

I lean to option 2 for a "calm" app. The streak and the insight engine carry motivation better than a bar. You already offer "Just log" mode which hides all of it, which suggests you half agree.

---

## 7. Screen by screen

### Home
Up to 13 elements can stack: greeting and level ring, update card, crash card, nutrition rings, stat row, today card, calorie suggestion, readiness, new badge, bridge card, height prompt, streak, progress.

- Why does protein appear in the ring centre and again in the stat row directly under it? (Accepted in the handoff. I would still ask.)
- Why does a crash report card sit in the main feed above nutrition?
- Why can update, calorie suggestion, new badge and height prompt all ask for attention on the same morning? One "needs you" slot that shows only the most important would calm the screen.
- Why is the streak, the strongest habit signal, near the bottom, below a prompt card?
- Why is Progress reachable from four places (rank ring, progress card, Profile, Coach) while body weight has one small tile?
- Is the name-based easter egg (`RaadEgg`) meant to ship to everyone?

### Workouts
- Three header icons (Library, History, Edit) plus a quick-workout pill plus a pinned Start. History is a destination, not an action; it belongs with Progress.
- The muscle recovery map is one of the most distinctive things in the app and it lives at the bottom of this tab. Is that the right home, or does it belong next to Readiness?

### Live workout
- Nothing new beyond the handoff list. The keep-screen-on fix there matters more than it looks.

### Food
- Logging is three taps, which is fine.
- With AI off, what is the fast path for a home-cooked meal that is not in the catalogue? "Quick log" is in an overflow menu. If the camera goes away for most users, typed quick-add deserves a visible place.
- Saved meals or "copy yesterday's breakfast": is there one? Recents exist; a repeatable combined meal would cover most real days.
- Water is not tracked. Deliberate? It is a common request and a cheap one.

### Profile and Settings
- A gear icon and a Settings card on the same screen (the handoff already removes the gear).
- `debriefEnabled` exists in settings and backups with no switch anywhere. Remove the setting's effect or give it a row. Keep the backup field.
- Hand length and palm width are asked for the AI photo scan. If the scan becomes a minor feature, these two rows are odd things to find under "Body".
- Two unused components: `SettingsSection` and `ShortcutCard` in `ProfileComponents.kt`.

### Onboarding
- Why is a name required to continue? It is used for a greeting.
- **Default targets look real.** Calories default to 2,200 and protein to 140 g for everyone. Height, age and goal are asked later, and the prompt for them appears only once a workout exists. So someone who opens Food first is shown rings against goals that were never calculated for them. Either ask weight and height up front (two fields), or show "Set your targets" in place of a ring until they are set. Worth confirming on a fresh install.

### Notifications
- One reminder string and a rest timer. Calm, good. The reminder is the cheapest place to show the insight engine.
- No weekly summary. A Sunday "your week" notification leading to the Progress tab is a natural fit and can be off by default.

---

## 8. Wording

### What is good
The copy is calm and adult. Across 1,379 strings: "Great" 3 times, "Nice" once, "Awesome" never, "crush" never, exclamation marks in 2 strings. That is unusual and worth protecting. Sentences like "One workout is not a trend" and "Owning a weight comes before adding more" are the right voice.

### What is weak
- **Sameness, not cheesiness.** The lines people see daily have one version each: "Done for today. Nice work.", "Train as planned.", "Recovery is part of training.", "Today: Push".
- **Abstract where it could be specific.** "Clear progress today. Your numbers went up on most exercises." has no number in it. "Up on 4 of 5. Pull-ups led: 8, 8, 7 to 9, 8, 8." is the same fact and far more rewarding. The headline for "some up" already does this; the others should.
- **The "why this weight" lines are dry.** "Same as last time: finish every set first" reads like an error message. This is the coach talking at the most important moment. Try "Same numbers today. Finish all three sets and next time goes up."
- **Praise leans on a few ideas.** "Showing up", "the habit", "consistency" carry most of the encouraging lines. After a month the user has read the same thought in nine sentences.
- **Errors dominate.** "couldn't" 23 times and "try again" 20 times. Most belong to AI and Drive; cutting AI removes many.
- **Mixed terms for one thing:** exercise 36 / move 14; workout 68 / session 10; kcal 19 / calories 11 / cal 2; plan 68 / routine 2. Pick one of each and write it into `DESIGN.md`.
- **27 strings are over 120 characters.** The longest is 392. On a phone that is a wall. Most are explanations of the bridge and readiness.

### Loose ends
- `meals_doc_placeholder` exists in English and is missing in Arabic.
- 20 strings have no reference in code (for example `tour_2_t`, `tour_6_b`, `coach_add_key`, `onb_skip`, `live_reminder_body`). Check each before removing.
- Two hard-coded English texts: `AppTour.kt:80` and a placeholder at `ProfileSections.kt:739`.

### A voice guide would help
One page in `DESIGN.md`: who is speaking (a calm coach who has seen your numbers), three rules (always a number when one exists; never the same sentence two days running; say what to do next), the chosen terms, and ten before-and-after pairs. Then every future string, in both languages, has something to be checked against.

---

## 9. What is not here and should be asked about

| Missing | Why it matters | Size |
|---|---|---|
| Exercise instructions and cues | Without an AI coach this is the coaching | Large (writing) |
| Lifetime totals and milestones | Free delight from data you already have | Small |
| Share card after a workout | Pride for the user, reach for the app | Medium |
| Deload or lighter week in plans | The plan text already promises it | Medium |
| Stretches and a real cool-down | A switch exists that does nothing | Medium |
| Cardio logging | Builder offers cardio with 2 exercises | Medium |
| Rest and pace insights | `doneAt` is stored on every set and unused | Small |
| Body measurements (waist) | Scale weight alone misleads people building muscle | Small |
| Progress photos | Common request; private, local, fits your backup | Medium |
| Weekly summary | A reason to open the app on a rest day | Small |
| Supersets | Shorter sessions for the "minutes" answer | Medium |
| Water | Cheap, commonly expected | Small |
| Plate calculator, warm-up set suggestion | Helps gym users at the bar | Small |

Stored but never used for anything: set finish times, day notes, plan item notes, the plan description, the time of day of weigh-ins.

---

## 10. Suggested order

1. **Truth fixes** (small): remove or implement the "5th week" promise, hide the cool-down switch until stretches exist, fix the two Hemo plan sentences and the "no equipment" claim, show the debrief footnote only on low-recovery days, make AI memory ask before it saves.
2. **Finish screen rebuild**: day-type layout, debrief first, PR and milestone moments, better rotation, score decision.
3. **Insight engine, first slice**: engine plus about 15 kinds on the finish screen, then the reminder notification.
4. **AI reduction**: chat, check-in, memory, Groq, model pickers and the bridge worker out; scan, label and plan-paste stay. Fourth tab becomes Progress with the insight list.
5. **Exercise content**: equipment field, cues for the 100, then new exercises and stretches.
6. **Plans**: more starters as builder presets, "different version", a four-week cycle.
7. **Gamification decision**, then badges for gym lifts and lifetime totals.
8. **Voice guide and a wording pass** in both languages.

Steps 1 and 2 are the ones a user would notice within a day.

---

## 11. Questions only you can answer

1. Who is this for: you and friends, or strangers on a store? That decides how much onboarding, gym content and polish is worth it.
2. Is the calisthenics identity (Hemo plan, skill badges) the heart of the app, or a starting point you want to grow past?
3. Do you want a daily score at all?
4. XP and levels: make them unlock things, or shrink them?
5. Is the chat gone for good, or parked behind a key for you personally?
6. Is cardio in scope, or is this a strength app that says so?
7. Is the web PWA still a goal? Every wording and content change here doubles if it is.
8. How much writing are you willing to do or review in Arabic? The insight engine and exercise cues are mostly a writing job, and that is where the quality will come from.
