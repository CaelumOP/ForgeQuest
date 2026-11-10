package com.forgequest.app.data.seed

import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.plan.PlanDraftDay
import com.forgequest.app.data.plan.PlanDraftItem

/** Ready-made plans offered during onboarding. See docs/hemo-foundation.md for the source of the first one. */
object StarterPlans {

    private fun warmUp() = PlanDraftItem("jumping_jacks", block = "Warm-up", circuit = false, sets = 5, repsMin = 10, restSeconds = 20)

    private fun core(vararg items: PlanDraftItem) = items.map { it.copy(block = "Core", circuit = true, sets = 3) }
    private fun main(vararg items: PlanDraftItem) = items.map { it.copy(block = "Main", circuit = true, sets = 3) }
    private fun legs(vararg items: PlanDraftItem) = items.map { it.copy(block = "Legs", circuit = true, sets = 3) }

    private fun reps(id: String, min: Int, max: Int? = null) = PlanDraftItem(id, repsMin = min, repsMax = max, restSeconds = 30)
    private fun hold(id: String, seconds: Int) = PlanDraftItem(id, seconds = seconds, restSeconds = 30)

    val hemoFoundation = PlanDraft(
        name = "Hemo · Foundation level",
        description = "Hemo Calisthenics' 5-day foundation routine. Jumping jacks to warm up, then each block as a circuit for 3–4 rounds. Do the reps you can; the number is the target.",
        days = listOf(
            PlanDraftDay(1, "Day 1 · Core + upper body", items = listOf(warmUp()) + core(
                hold("incline_hollow_plank", 30), reps("hip_bridge", 10), reps("crunch", 10), hold("superman_hold", 20),
            ) + main(
                reps("knee_scapular_push_up", 10), reps("scapular_australian_pull_up", 10),
                reps("bent_leg_bench_dip", 10), reps("scapular_australian_chin_up", 10),
            )),
            PlanDraftDay(2, "Day 2 · Upper body", items = listOf(warmUp()) + main(
                reps("knee_push_up", 10), reps("australian_pull_up", 8, 10),
                reps("reverse_grip_bench_dip", 10), reps("australian_chin_up", 8, 10),
            )),
            PlanDraftDay(3, "Day 3 · Core + legs", items = listOf(warmUp()) + core(
                hold("incline_hollow_plank", 30), hold("incline_reverse_plank", 30), reps("crunch", 10), hold("superman_hold", 20),
            ) + legs(
                reps("half_squat", 10, 15), reps("half_wide_squat", 10, 15), reps("half_narrow_squat", 10, 15),
                reps("hip_bridge", 10, 15), reps("calf_raise_turned_out", 20), reps("calf_raise", 20),
            )),
            PlanDraftDay(4, "Rest day", isRest = true, note = "Full rest. Hemo recommends a complete break after day 3."),
            PlanDraftDay(5, "Day 4 · Core + upper body", items = listOf(warmUp()) + core(
                hold("incline_hollow_plank", 30), reps("hip_bridge", 10), reps("crunch", 10), hold("superman_hold", 20),
            ) + main(
                reps("wide_knee_push_up", 10), hold("dead_hang", 20),
                reps("bent_leg_bench_dip", 10, 15), reps("australian_chin_up", 10),
            )),
            PlanDraftDay(6, "Day 5 · Upper body", items = listOf(warmUp()) + main(
                reps("incline_diamond_knee_push_up", 8), reps("australian_pull_up", 10),
                reps("reverse_grip_bench_dip", 10, 15), reps("australian_chin_up", 10),
            )),
            PlanDraftDay(7, "Rest day", isRest = true, note = "Full rest, then repeat the week. Once this feels easy, try a harder variation of each move."),
        ),
    )

    val fullBodyBeginner = PlanDraft(
        name = "Full body · 3 days · bodyweight",
        description = "Simple bodyweight plan: Monday, Wednesday, Friday. Bodyweight only; the rows need a low bar or a sturdy table.",
        days = (1..7).map { dow ->
            if (dow == 1 || dow == 3 || dow == 5) {
                PlanDraftDay(
                    dow, "Full body",
                    items = listOf(
                        PlanDraftItem("push_up", block = "Main", sets = 3, repsMin = 8, repsMax = 12, restSeconds = 90),
                        PlanDraftItem("australian_pull_up", block = "Main", sets = 3, repsMin = 8, repsMax = 12, restSeconds = 90),
                        PlanDraftItem("squat", block = "Main", sets = 3, repsMin = 12, repsMax = 15, restSeconds = 90),
                        PlanDraftItem("hip_bridge", block = "Main", sets = 3, repsMin = 12, restSeconds = 60),
                        PlanDraftItem("plank", block = "Main", sets = 3, seconds = 30, restSeconds = 60),
                    ),
                )
            } else {
                PlanDraftDay(dow, "Rest day", isRest = true)
            }
        },
    )

    /** The same three days with dumbbells, for a gym or a home set: what "Full gym" people expect to see. */
    val fullBodyDumbbells = PlanDraft(
        name = "Full body · 3 days · dumbbells",
        description = "Simple full-body plan with dumbbells: Monday, Wednesday, Friday. For a gym or a home set.",
        days = (1..7).map { dow ->
            if (dow == 1 || dow == 3 || dow == 5) {
                PlanDraftDay(
                    dow, "Full body",
                    items = listOf(
                        PlanDraftItem("goblet_squat", block = "Main", sets = 3, repsMin = 8, repsMax = 12, restSeconds = 90),
                        PlanDraftItem("db_bench_press", block = "Main", sets = 3, repsMin = 8, repsMax = 12, restSeconds = 90),
                        PlanDraftItem("db_row", block = "Main", sets = 3, repsMin = 8, repsMax = 12, restSeconds = 90),
                        PlanDraftItem("db_shoulder_press", block = "Main", sets = 3, repsMin = 8, repsMax = 12, restSeconds = 90),
                        PlanDraftItem("plank", block = "Main", sets = 3, seconds = 30, restSeconds = 60),
                    ),
                )
            } else {
                PlanDraftDay(dow, "Rest day", isRest = true)
            }
        },
    )

    val pushPullLegs = PlanDraft(
        name = "Push · Pull · Legs (gym)",
        description = "6-day gym split. Each muscle twice a week.",
        days = (1..7).map { dow ->
            when (dow) {
                1, 4 -> PlanDraftDay(dow, "Push", items = listOf(
                    PlanDraftItem("bench_press", sets = 4, repsMin = 6, repsMax = 10, restSeconds = 150),
                    PlanDraftItem("overhead_press", sets = 3, repsMin = 8, repsMax = 10, restSeconds = 120),
                    PlanDraftItem("incline_db_press", sets = 3, repsMin = 10, repsMax = 12, restSeconds = 90),
                    PlanDraftItem("lateral_raise", sets = 3, repsMin = 12, repsMax = 15, restSeconds = 60),
                    PlanDraftItem("tricep_pushdown", sets = 3, repsMin = 10, repsMax = 15, restSeconds = 60),
                ))
                2, 5 -> PlanDraftDay(dow, "Pull", items = listOf(
                    PlanDraftItem("pull_up", sets = 4, repsMin = 6, repsMax = 10, restSeconds = 150),
                    PlanDraftItem("barbell_row", sets = 3, repsMin = 8, repsMax = 10, restSeconds = 120),
                    PlanDraftItem("lat_pulldown", sets = 3, repsMin = 10, repsMax = 12, restSeconds = 90),
                    PlanDraftItem("face_pull", sets = 3, repsMin = 12, repsMax = 15, restSeconds = 60),
                    PlanDraftItem("bicep_curl", sets = 3, repsMin = 10, repsMax = 12, restSeconds = 60),
                ))
                3, 6 -> PlanDraftDay(dow, "Legs", items = listOf(
                    PlanDraftItem("back_squat", sets = 4, repsMin = 6, repsMax = 10, restSeconds = 180),
                    PlanDraftItem("romanian_deadlift", sets = 3, repsMin = 8, repsMax = 10, restSeconds = 120),
                    PlanDraftItem("leg_press", sets = 3, repsMin = 10, repsMax = 12, restSeconds = 90),
                    PlanDraftItem("leg_curl", sets = 3, repsMin = 10, repsMax = 12, restSeconds = 60),
                    PlanDraftItem("standing_calf_raise", sets = 4, repsMin = 12, repsMax = 15, restSeconds = 60),
                ))
                else -> PlanDraftDay(dow, "Rest day", isRest = true)
            }
        },
    )

    // ---- More plans, offered next to the onboarding four in the plan library ----

    private fun lift(id: String, sets: Int, min: Int, max: Int, rest: Int = 90) =
        PlanDraftItem(id, block = "Main", sets = sets, repsMin = min, repsMax = max, restSeconds = rest)

    private fun holdSet(id: String, sets: Int, seconds: Int, rest: Int = 60) =
        PlanDraftItem(id, block = "Main", sets = sets, seconds = seconds, restSeconds = rest)

    /** A week from [trained] (1 = Monday): every other day is a rest day. */
    private fun week(trained: Map<Int, Pair<String, List<PlanDraftItem>>>) = (1..7).map { dow ->
        trained[dow]?.let { (title, items) -> PlanDraftDay(dow, title, items = items) } ?: PlanDraftDay(dow, "Rest day", isRest = true)
    }

    val fullBodyGym = PlanDraft(
        name = "Full body · 3 days · gym",
        description = "Barbell and machine plan: Monday, Wednesday, Friday. A squat or hinge, a press and a pull every session.",
        days = week(mapOf(
            1 to ("Full body" to listOf(
                lift("back_squat", 3, 6, 8, 150), lift("bench_press", 3, 6, 8, 150), lift("barbell_row", 3, 8, 10, 120),
                lift("lateral_raise", 2, 12, 15, 60), holdSet("plank", 3, 30),
            )),
            3 to ("Full body" to listOf(
                lift("romanian_deadlift", 3, 8, 10, 120), lift("overhead_press", 3, 6, 8, 120), lift("lat_pulldown", 3, 8, 12),
                lift("leg_press", 3, 10, 12), lift("bicep_curl", 2, 10, 12, 60),
            )),
            5 to ("Full body" to listOf(
                lift("back_squat", 3, 8, 10, 120), lift("incline_db_press", 3, 8, 12), lift("pull_up", 3, 6, 10, 120),
                lift("leg_curl", 3, 10, 12, 60), lift("tricep_pushdown", 2, 10, 15, 60),
            )),
        )),
    )

    val upperLowerGym = PlanDraft(
        name = "Upper / lower · 4 days · gym",
        description = "4-day gym split: upper body on Monday and Thursday, lower body on Tuesday and Friday.",
        days = week(mapOf(
            1 to ("Upper body" to listOf(
                lift("bench_press", 4, 6, 8, 150), lift("barbell_row", 4, 6, 8, 150), lift("overhead_press", 3, 8, 10, 120),
                lift("lat_pulldown", 3, 10, 12), lift("bicep_curl", 2, 10, 12, 60), lift("tricep_pushdown", 2, 10, 12, 60),
            )),
            2 to ("Lower body" to listOf(
                lift("back_squat", 4, 6, 8, 180), lift("romanian_deadlift", 3, 8, 10, 120), lift("leg_press", 3, 10, 12),
                lift("leg_curl", 3, 10, 12, 60), lift("standing_calf_raise", 3, 12, 15, 60),
            )),
            4 to ("Upper body" to listOf(
                lift("incline_db_press", 3, 8, 12), lift("pull_up", 3, 6, 10, 120), lift("lateral_raise", 3, 12, 15, 60),
                lift("face_pull", 3, 12, 15, 60), lift("hammer_curl", 2, 10, 12, 60), lift("skull_crusher", 2, 10, 12, 60),
            )),
            5 to ("Lower body" to listOf(
                lift("hip_thrust", 3, 8, 10, 120), lift("leg_press", 3, 10, 15), lift("bulgarian_split_squat", 3, 8, 10, 90),
                lift("leg_curl", 3, 10, 12, 60), lift("standing_calf_raise", 3, 12, 15, 60),
            )),
        )),
    )

    val upperLowerDumbbells = PlanDraft(
        name = "Upper / lower · 4 days · dumbbells",
        description = "4-day plan with dumbbells and your body weight: upper body on Monday and Thursday, lower body on Tuesday and Friday.",
        days = week(mapOf(
            1 to ("Upper body" to listOf(
                lift("db_bench_press", 3, 8, 12), lift("db_row", 3, 8, 12), lift("db_shoulder_press", 3, 8, 12),
                lift("hammer_curl", 2, 10, 12, 60), lift("push_up", 2, 10, 15, 60),
            )),
            2 to ("Lower body" to listOf(
                lift("goblet_squat", 3, 8, 12), lift("db_romanian_deadlift", 3, 8, 12), lift("db_lunge", 3, 8, 10),
                lift("db_calf_raise", 3, 12, 15, 60), holdSet("plank", 3, 30),
            )),
            4 to ("Upper body" to listOf(
                lift("incline_db_press", 3, 8, 12), lift("db_row", 3, 10, 12), lift("lateral_raise", 3, 12, 15, 60),
                lift("bicep_curl", 2, 10, 12, 60), lift("diamond_push_up", 2, 8, 12, 60),
            )),
            5 to ("Lower body" to listOf(
                lift("bulgarian_split_squat", 3, 8, 10), lift("single_leg_rdl", 3, 8, 10), lift("goblet_squat", 3, 10, 15),
                lift("hip_bridge", 3, 12, 15, 60), holdSet("side_plank", 2, 30),
            )),
        )),
    )

    val calisthenicsLevel2 = PlanDraft(
        name = "Calisthenics · level 2",
        description = "Needs a pull-up bar and dip bars. Harder push-ups, pull-ups and one-leg moves, plus L-sit holds, four days a week.",
        days = week(mapOf(
            1 to ("Push + core" to listOf(
                lift("diamond_push_up", 4, 8, 12), lift("pseudo_planche_push_up", 3, 5, 8), lift("dip", 3, 6, 10, 120),
                lift("pike_push_up", 3, 6, 10), holdSet("l_sit", 4, 10),
            )),
            2 to ("Pull + legs" to listOf(
                lift("pull_up", 4, 5, 8, 120), lift("chin_up", 3, 5, 8, 120), lift("cossack_squat", 3, 6, 8),
                lift("bulgarian_split_squat", 3, 8, 10), lift("pistol_squat", 3, 3, 5, 120),
            )),
            4 to ("Push + core" to listOf(
                lift("dip", 4, 6, 10, 120), lift("diamond_push_up", 3, 10, 15), lift("pike_push_up", 3, 8, 10),
                lift("hanging_knee_raise", 3, 8, 12), holdSet("hollow_hold", 3, 30),
            )),
            5 to ("Pull + legs" to listOf(
                lift("chin_up", 4, 5, 8, 120), lift("pull_up", 3, 5, 8, 120), lift("pistol_squat", 4, 3, 5, 120),
                lift("cossack_squat", 3, 8, 10), holdSet("l_sit", 4, 12),
            )),
        )),
    )

    val minimalTwoDay = PlanDraft(
        name = "Minimal · 2 days",
        description = "Two short full-body sessions a week with one pair of dumbbells: Monday and Thursday. Enough to keep strength going.",
        days = week(mapOf(
            1 to ("Full body" to listOf(
                lift("goblet_squat", 3, 8, 12), lift("db_bench_press", 3, 8, 12), lift("db_row", 3, 8, 12), holdSet("plank", 2, 30),
            )),
            4 to ("Full body" to listOf(
                lift("db_romanian_deadlift", 3, 8, 12), lift("db_shoulder_press", 3, 8, 12), lift("db_row", 3, 10, 12), holdSet("side_plank", 2, 30),
            )),
        )),
    )

    val zeroEquipment = PlanDraft(
        name = "Zero equipment · 3 days",
        description = "No equipment at all, no bar and no table: Monday, Wednesday, Friday. Floor, wall and your body weight.",
        days = week(mapOf(
            1 to ("Full body" to listOf(
                lift("push_up", 3, 8, 12, 60), lift("squat", 3, 12, 15, 60), lift("hip_bridge", 3, 12, 15, 45),
                holdSet("superman_hold", 3, 20, 45), holdSet("plank", 3, 30, 45),
            )),
            3 to ("Full body" to listOf(
                lift("wide_knee_push_up", 3, 10, 15, 60), lift("lunge", 3, 8, 12, 60), lift("calf_raise", 3, 15, 20, 45),
                lift("bird_dog", 3, 8, 12, 45), lift("mountain_climber", 3, 20, 30, 45),
            )),
            5 to ("Full body" to listOf(
                lift("push_up", 3, 8, 12, 60), lift("cossack_squat", 3, 6, 10, 60), lift("hip_bridge", 3, 12, 15, 45),
                holdSet("side_plank", 2, 30, 45), holdSet("hollow_hold", 3, 20, 45),
            )),
        )),
    )

    /** English starter texts and their formal Arabic versions. */
    val arabic = mapOf(
        "Hemo · Foundation level" to "هيمو · المستوى التأسيسي",
        "Hemo Calisthenics' 5-day foundation routine. Jumping jacks to warm up, then each block as a circuit for 3–4 rounds. Do the reps you can; the number is the target."
            to "روتين Hemo Calisthenics التأسيسي على خمسة أيام: قفز مع فتح الذراعين للإحماء، ثم كل مجموعة تمارين في دائرة من ⁦3–4⁩ جولات. أدِّ ما تستطيع من تكرارات؛ فالرقم هدف لا شرط.",
        "Full body · 3 days" to "الجسم كاملًا · 3 أيام",
        "Simple full-body plan: Monday, Wednesday, Friday. Good for gym or home." to "خطة بسيطة للجسم كاملًا: الاثنين والأربعاء والجمعة، تصلح للنادي أو المنزل.",
        "Full body · 3 days · bodyweight" to "الجسم كاملًا · 3 أيام · وزن الجسم",
        "Simple bodyweight plan: Monday, Wednesday, Friday. Bodyweight only; the rows need a low bar or a sturdy table." to "خطة بسيطة بوزن الجسم: الاثنين والأربعاء والجمعة، بوزن الجسم فقط، وتحتاج تمارين السحب إلى عارضة منخفضة أو طاولة متينة.",
        "Full body · 3 days · dumbbells" to "الجسم كاملًا · 3 أيام · دمبل",
        "Simple full-body plan with dumbbells: Monday, Wednesday, Friday. For a gym or a home set." to "خطة بسيطة للجسم كاملًا بالدمبل: الاثنين والأربعاء والجمعة، للنادي أو للمعدات المنزلية.",
        "Push · Pull · Legs (gym)" to "دفع · سحب · أرجل (النادي)",
        "6-day gym split. Each muscle twice a week." to "تقسيم على ستة أيام في النادي، تُمرَّن فيه كل عضلة مرتين أسبوعيًا.",
        "Day 1 · Core + upper body" to "اليوم 1 · الجذع والجزء العلوي",
        "Day 2 · Upper body" to "اليوم 2 · الجزء العلوي",
        "Day 3 · Core + legs" to "اليوم 3 · الجذع والأرجل",
        "Day 4 · Core + upper body" to "اليوم 4 · الجذع والجزء العلوي",
        "Day 5 · Upper body" to "اليوم 5 · الجزء العلوي",
        "Rest day" to "يوم راحة",
        "Full body" to "الجسم كاملًا",
        "Push" to "دفع",
        "Pull" to "سحب",
        "Legs" to "أرجل",
        "Full rest. Hemo recommends a complete break after day 3." to "راحة تامة؛ يوصي Hemo براحة كاملة بعد اليوم الثالث.",
        "Full body · 3 days · gym" to "الجسم كاملًا · 3 أيام · النادي",
        "Barbell and machine plan: Monday, Wednesday, Friday. A squat or hinge, a press and a pull every session." to "خطة بالبار والأجهزة: الاثنين والأربعاء والجمعة، وفي كل حصة قرفصاء أو انثناء للورك، ثم تمرين دفع وآخر سحب.",
        "Upper / lower · 4 days · gym" to "علوي / سفلي · 4 أيام · النادي",
        "4-day gym split: upper body on Monday and Thursday, lower body on Tuesday and Friday." to "تقسيم على أربعة أيام في النادي: الجزء العلوي الاثنين والخميس، والجزء السفلي الثلاثاء والجمعة.",
        "Upper / lower · 4 days · dumbbells" to "علوي / سفلي · 4 أيام · دمبل",
        "4-day plan with dumbbells and your body weight: upper body on Monday and Thursday, lower body on Tuesday and Friday." to "خطة على أربعة أيام بالدمبل ووزن الجسم: الجزء العلوي الاثنين والخميس، والجزء السفلي الثلاثاء والجمعة.",
        "Calisthenics · level 2" to "كاليستنكس · المستوى الثاني",
        "Needs a pull-up bar and dip bars. Harder push-ups, pull-ups and one-leg moves, plus L-sit holds, four days a week." to "تحتاج إلى عقلة ومتوازي. ضغط وعقلة وتمارين الساق الواحدة بصعوبة أعلى، مع ثبات الجلوس على شكل L، أربعة أيام أسبوعيًا.",
        "Minimal · 2 days" to "الحد الأدنى · يومان",
        "Two short full-body sessions a week with one pair of dumbbells: Monday and Thursday. Enough to keep strength going." to "حصتان قصيرتان أسبوعيًا للجسم كاملًا بزوج دمبل واحد: الاثنين والخميس، وهما كافيتان للحفاظ على القوة.",
        "Zero equipment · 3 days" to "بلا معدات · 3 أيام",
        "No equipment at all, no bar and no table: Monday, Wednesday, Friday. Floor, wall and your body weight." to "بلا أي معدات، لا عقلة ولا طاولة: الاثنين والأربعاء والجمعة. الأرض والحائط ووزن جسمك فقط.",
        "Upper body" to "الجزء العلوي",
        "Lower body" to "الجزء السفلي",
        "Push + core" to "دفع والجذع",
        "Pull + legs" to "سحب وأرجل",
        "Full rest, then repeat the week. Once this feels easy, try a harder variation of each move." to "راحة تامة، ثم كرّر الأسبوع. متى صار هذا سهلًا فجرّب نسخة أصعب من كل تمرين.",
    )

    private fun localize(p: PlanDraft): PlanDraft {
        if (java.util.Locale.getDefault().language != "ar") return p
        fun t(s: String) = arabic[s] ?: s
        return p.copy(name = t(p.name), description = t(p.description), days = p.days.map { it.copy(title = t(it.title), note = t(it.note)) })
    }

    /** In the reader's language; applied plans keep the names they were saved with. */
    val all: List<PlanDraft> get() = listOf(hemoFoundation, fullBodyBeginner, fullBodyDumbbells, pushPullLegs).map(::localize)

    /** The extra plans, shown after [all] in the plan library; onboarding does not use them. */
    val more: List<PlanDraft> get() = listOf(
        fullBodyGym, upperLowerGym, upperLowerDumbbells, calisthenicsLevel2, minimalTwoDay, zeroEquipment,
    ).map(::localize)

    /** Positions in [all], for onboarding's equipment ↔ plan matching. */
    const val BODYWEIGHT_INDEX = 1
    const val DUMBBELLS_INDEX = 2
    const val GYM_SPLIT_INDEX = 3
}
