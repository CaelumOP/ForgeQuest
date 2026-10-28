# Data and kit API (for screen workers)

All data functions are async unless they are hooks (`use*`). Row types come from `src/data/db.ts`. Names follow the Kotlin repositories.

## src/data/settings.ts
loadSettings(); updateSettings(fn); useSettings(); weightUnit(s); configuredEquipment(s); toBackupSettings(s); fromBackupSettings(b, s); weekTargets(); saveWeekTargets(map); clearWeekTargets(); DEFAULT_SETTINGS.

## src/data/live.ts
useLive<T>(query, deps, initial)

## src/data/workouts.ts
- Library: seedLibrary(lang); getLibrary() / useLibrary(); addCustomExercise(name, kind, primary, videoUrl); usage(id); updateExercise; mergeExercise(from, to); deleteExercise(id); isWebUrl; setVideoUrl(id, url)
- Plans: getActivePlan() / useActivePlan(); getAllPlans() / useAllPlans(); trainingDays(plan); planDay(plan, dow); planToDraft(plan); applyPlan(draft, source); activatePlan(id); deletePlan(id); movePlanItem(item, delta); swapPlanDays(planId, a, b); updatePlanItem; deletePlanItem(id); restorePlanItem; updatePlanDay; swapPlanItem(item, newId); addPlanItem(dayId, exId)
- Live session: openSessionNow() / useOpenSession(); getSets(sid) / useSets(sid); startSession(dayOrNull, lighter?); targetLabel(kind, item); updateSet(set); removeSet(id); addSet(sid, pos); addExercise(sid, exId); swapExercise(sid, pos, newId); discardSession(sid); finishSession(sid, tickAll?) → FinishResult
- History: recentDoneSets(exId); recentDoneSetsAll(exId); finishedSessions() / useFinishedSessions(); getSession(id) / useSession(id); allDoneSets(); updateFinishedSet(set); deleteFinishedSet(set); moveSession(sid, epochDay); deleteSession(sid); recomputeRecords(exId)
- Debrief: workoutDebrief(sid) / useWorkoutDebrief(sid): the calculated debrief of a finished workout (domain/debrief.ts), never stored; the AI generateDebrief is removed
- A few texts (rest day, quick workout, hold ceiling) are English constants here: map them to i18n keys in the screen.

## src/data/progress.ts
getProgressSnapshot(now?) / useProgress(); evaluateBadges(); markBadgesSeen(); logWeight(kg); setDayTag(epochDay, tag | null); EXCUSE_BACK_DAYS

## src/data/food.ts
getFoods() / useFoods(); getMealsOn(day) / useMealsOn(day); saveFood(row); deleteFood(id); deleteMeal(id); restoreMeal(meal); logFoods([[food, qty]], day?); logOnce(name, cat, kcal, protein, day?, carbs?); categoryForNow(hour?); totalsOf(meals); dayTotals(day?)

## src/ui/kit (import from '../kit')
Card, SectionHeader, Button(variant filled|tonal|text, loading), IconButton(label), Chip, ChipGroup(single|multiple), ListRow(title, subtitle, icon, trailing, href|onClick), Stat(value, label, accent), EmptyState, Ring(value 0..1, color/track CSS var names, label), LineChart / BarChart(data {label, value}[], title), Sheet(open, onClose, title), Dialog(confirm), toast(msg), TextField, NumberField, Stepper, Markdown(text). Icons: src/ui/icons.tsx.

## Strings
t(key, ...args) and plural(key, n) from src/i18n. Use the Android keys (src/i18n/en.json). Web-only keys go in YOUR OWN pair `src/i18n/web/<feature>.en.json` + `.ar.json` (formal Arabic), keys prefixed with your feature (e.g. "live_…"). Never edit another feature's file.

## src/data/coach-chat.ts, src/data/debrief-text.ts
- askCoachAboutWorkout(sid) leaves the debrief message for the Coach screen (takePendingCoachMessage() reads it); debriefChatMessage(sid): the message text; sendCoachMessage(text, aboutWorkout?); canDebrief(settings)
- debriefLines(result, lang, name, weight): the debrief's words (headline, wins, watch, next, same rest, footnote); strings are the debrief_* keys
