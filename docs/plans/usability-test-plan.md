# First-time user test plan (Android)

Draft for review, 2026-10-25. Nothing runs until the open questions at the end are answered.

## Goal

See ForgeQuest the way a new tester does. They know one thing: "it's a workout app". Everything else (food tracking, the AI coach, Drive backup, the Gemini bridge, Fitbit readiness) they have to find on their own.

For each feature we want to know:

1. **Noticed?** Did the user spot it while looking around, with no task given?
2. **Found?** Given a goal ("make sure you don't lose your workouts"), did they get there? How many taps? Any wrong turns?
3. **Understood?** Did the label and the screen explain what it does, in their words?
4. **Finished?** Could they complete the job without help?

## Setup

- Emulator `ForgeQuest_Pixel`, app data wiped, CI APK from `main` (2.11.2 if the readiness fix lands first).
- Screens are read from emulator screenshots only. No looking at code during a session.
- Every step is logged: the screenshot, what the "user" thinks they see, what they tap and why, and what surprised them.
- The "user" gets each task as a goal in everyday words. They never hear product words (Coach, Gemini, Drive, Health Connect, readiness, XP) unless a screen showed them first.
- Help rule: if stuck for 6 tries on one task, mark it **failed**, note where they got lost, then show the way so later tasks can go on.

## The personas

Each persona is played by a fresh agent that has never seen the code or the earlier sessions. All they know is "it's a workout app" plus their own background.

| Persona | Background | Main rounds |
|---|---|---|
| **Sam**, 24 | Trains 3 to 4 times a week, gym or home dumbbells. Android phone and a Fitbit. Wants to stop quitting after two weeks. Has never heard of an "API key". | 1, 2 (all), 5 |
| **Nora**, 38 | Complete beginner. Trains at home, no equipment. Wants to lose weight. Gym words like "sets", "RPE" and "superset" put her off. Mostly cares about food. | 1, 2A, 2B, 2F |
| **Omar**, 19 | Student, kettlebell and dumbbells at home. Loves games and streaks, no Fitbit. Wants someone to tell him what to do. | 1, 2A, 2C, 2F |
| **Lina**, 29 | Logs everything; switching from a calorie-counter app. Has a Fitbit. Worries about losing her data. | 1, 2B, 2D, 2E, 3 |
| **Khalid**, 45 | Reads only Arabic, phone set to Arabic, not technical. Walks and does light dumbbell work. | 4 (with 1, A1, B2, D1, E2) |
| **Returning Sam** | Sam, two weeks in, with seeded data. | 3 |

The tasks below are written for Sam; each persona gets the same goals in their own words.

## Round 1: First look (no tasks)

| # | What happens | What we watch |
|---|---|---|
| 1.1 | Open the app for the first time and go through whatever it shows. | Does the tour get read or skipped? After it, ask: "What do you think this app does?" Do food, coach and Fitbit come up in the answer? |
| 1.2 | Onboarding: name, body, goals, equipment, plan, AI key. | Which steps feel unclear? Does Sam understand the protein and calorie numbers? What does Sam do on the AI key step: try it, skip it, or get stuck? |
| 1.3 | Land on Home. Five-second test: "What is this screen telling you?" | Are the rings read as food (protein/calories)? Is the muscle map read as recovery? |
| 1.4 | Five minutes of free exploring. | List every tab and section Sam opens, in order. Note anything never opened (expected: Profile's settings groups). |

## Round 2: Goal tasks (empty app)

### A. Training

| # | Task given to Sam | Watching for |
|---|---|---|
| A1 | "Do today's workout and log your first exercise: 3 sets." | Finds Start, understands sets/reps/weight entry, notices the rest timer. |
| A2 | "You're resting. How long until the next set? Skip the rest, you feel fine." | Rest timer is clear and skippable. |
| A3 | "The bench is taken. Swap this exercise for something you can do instead." | Finds swap mid-workout; swap respects equipment. |
| A4 | "Leave halfway (press back), then come back to your workout." | Is data kept? Is "Resume" obvious? |
| A5 | "Finish the workout." Then: "Where can you see what you did today?" | Finish summary, then History. |
| A6 | "Your chest is still sore from yesterday. Is it ready to train? What other chest exercises could you do?" | Taps the muscle map; the 2.11 exercise sheet makes sense. |
| A7 | "Add one exercise to Tuesday and remove one you don't like." | Finds the plan editor. |
| A8 | "You found a programme on YouTube. Get it into the app." | Does Sam find the import path at all? (The tour mentions it once.) |
| A9 | "You only have dumbbells at home this week. Tell the app." | Finds equipment in settings. |

### B. Food (main focus)

| # | Task given to Sam | Watching for |
|---|---|---|
| B1 | No task. After A5, ask: "Is there anything else this app wants you to do today?" | Does Sam link the Home rings or the Food tab to eating without being asked? |
| B2 | "You just had chicken and rice. Write it down in the app." | Food tab vs Home; scan vs Add vs Quick log. Without an AI key, is the reason the scanner won't work clear, with a way forward? |
| B3 | "You eat the same breakfast every day. Make it quick to log tomorrow." | Food list, pin to top, one-tap log. |
| B4 | "How much protein do you still need today?" | Home ring vs Food tab numbers agree and read clearly. |
| B5 | "You want to lose a little fat. Make the app aim for fewer calories." | Finds Goals and nutrition in Profile; understands the calorie target (the 2.10 plan). |
| B6 | "You forgot to log last night's dinner. Add it to yesterday." | Past-day logging is findable. |
| B7 | "Take a photo of your plate and let the app work it out." | With a key: the scan flow and the review screen. Without one: the Gemini Gem / share path, if it shows. |
| B8 | "You logged the wrong food. Remove it." | Delete and undo. |

### C. AI coach and Gemini

| # | Task given to Sam | Watching for |
|---|---|---|
| C1 | "Your knee hurts when you squat. Ask the app what to do." | Does Sam find the Coach tab? The no-key screen explains why and what to do. |
| C2 | "Turn on the app's AI." | The key flow from zero: AI Studio link, copy, paste, check. Where does a non-technical person give up? |
| C3 | "Ask for a 3-day plan and start using it." | Review and apply; the plan shows up in Workouts. |
| C4 | "The app's answers are slow. Can you make them faster?" | Would Sam ever find Groq or the model setting? Expected: no. This measures how hidden they are. |
| C5 | "Make the app remember that you have a bad knee." | Coach memory and where it's shown. |
| C6 | "Your friend says the app can work with the Gemini app on your phone. Set that up." | Finds "Gemini app bridge"; do the label, hint, Copy prompt and Sync now make sense? Expected: hard. This measures by how much. |

### D. Backup and data

| # | Task given to Sam | Watching for |
|---|---|---|
| D1 | "You're getting a new phone next month. Make sure you don't lose your workouts." | Finds Backup and sync, and picks Drive or a file. Is "hidden app folder" reassuring or worrying? |
| D2 | "Your workouts are on Drive. Bring them back onto this phone." | Restore, then Undo last restore. |
| D3 | "Keep a copy of everything as a file." | File backup with no extra steps (the clean-UX rule). |
| D4 | "You wrote your plan in a Google Doc. Get it into the app." | Two places mention Docs (Workouts and Profile → Drive). Which does Sam find? Do the two doc names ("Workout Plan" vs "ForgeQuest plan") confuse? |

### E. Fitbit and readiness

| # | Task given to Sam | Watching for |
|---|---|---|
| E1 | "You wear a Fitbit. Make the app use your sleep and recovery." | Finds Fitbit readiness; the Health Connect permission screens. |
| E2 | "What does the app think you should do today: push or take it easy? Why?" | The readiness card and detail screen. Do Sleep %, Recovery % and Strain mean anything to Sam? |
| E3 | "You just synced your Fitbit. Make the app show last night." | Re-runs today's bug report after the fix: spinner, "Updated" or "still waiting" message. |

### F. Motivation and settings

| # | Task given to Sam | Watching for |
|---|---|---|
| F1 | "How many more workouts this week to keep your streak?" | The weekly streak idea and the target. |
| F2 | "What are badges? Which one could you get next?" | Badges screen. |
| F3 | "Make the app remind you to train." | Reminder; the 0–23 hour input. |
| F4 | "Switch weights to pounds. Make the app dark." | App and appearance. |
| F5 | "Lock the app so others can't open it." | App lock. |

## Round 3: Returning user (seeded data)

Import a made-up backup file with two weeks of workouts, meals and weights, then:

| # | Task given to Sam | Watching for |
|---|---|---|
| G1 | "Are you getting stronger? Show me." | Progress screen. |
| G2 | "What did you lift on bench press last week?" | History and the session screen. |
| G3 | Do a workout where the last two sessions hit 15+ reps per set. | The "ready for a harder version" note (open check from 2.11). |
| G4 | "You haven't trained in 9 days. What does the app say?" | Tone after a break: encouraging, not guilt-tripping (the anti-burnout goal). |

## Round 4: Arabic

Set the emulator to Arabic and repeat A1, A6 (the 2.11 sheet), B2, B5, C1, D1 and E2. Check right-to-left layout, clipped text, mixed digits, and formal wording. This also closes the open "Arabic for the 2.11 screens" check.

## Round 5: When things go wrong

| # | Situation | Watching for |
|---|---|---|
| H1 | Airplane mode, then scan a meal and ask the coach. | Clear offline messages; nothing lost. |
| H2 | Paste a wrong AI key. | The error says what to do next. |
| H3 | Rotate the phone and switch apps mid-workout. | Nothing is lost. |

## How findings get scored

- **Critical:** Sam couldn't finish a core job (log a workout, log food, keep data safe).
- **Major:** finished, but with wrong turns or real confusion.
- **Minor:** small friction, quickly worked around.
- **Hidden:** works, but a new user would never find it (expected for Groq, the bridge, and coach models).

Output: `docs/plans/usability-findings.md` with a table per round (task, result, taps, wrong turns, severity, screenshot), the top fixes ranked, and lettered options for each fix. No app changes until you pick.

## Known limits

- Whoever plays Sam is a stand-in for a person. A model can't fully forget what a "workout app" usually has, so these results help but can't replace a real tester.
- Google sign-in (Drive) and a real Gemini key need you to type into the emulator. I don't enter passwords or real keys.
- Fitbit data: the emulator has Health Connect, but test sleep data needs Google's "Health Connect Toolbox" app (a download I'd ask about first). Without it, E1 tests only the connect flow.
- About 2 to 3 hours of emulator time over all rounds. It can be split across sessions.
