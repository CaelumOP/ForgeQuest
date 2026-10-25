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
        description = "Hemo Calisthenics' 5-day foundation routine. Stretch, then jumping jacks, then each block as a circuit for 3–4 rounds. Do the reps you can; the number is the target.",
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
            PlanDraftDay(7, "Rest day", isRest = true, note = "Full rest, then repeat the week. Move to the Beginner level once this feels easy."),
        ),
    )

    val fullBodyBeginner = PlanDraft(
        name = "Full body · 3 days · bodyweight",
        description = "Simple bodyweight plan: Monday, Wednesday, Friday. Needs little or no equipment.",
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

    /** English starter texts and their formal Arabic versions. */
    val arabic = mapOf(
        "Hemo · Foundation level" to "هيمو · المستوى التأسيسي",
        "Hemo Calisthenics' 5-day foundation routine. Stretch, then jumping jacks, then each block as a circuit for 3–4 rounds. Do the reps you can; the number is the target."
            to "روتين Hemo Calisthenics التأسيسي على خمسة أيام: إطالة، ثم قفز مع فتح الذراعين، ثم كل مجموعة تمارين في دائرة من ⁦3–4⁩ جولات. أدِّ ما تستطيع من تكرارات؛ فالرقم هدف لا شرط.",
        "Full body · 3 days" to "الجسم كاملًا · 3 أيام",
        "Simple full-body plan: Monday, Wednesday, Friday. Good for gym or home." to "خطة بسيطة للجسم كاملًا: الاثنين والأربعاء والجمعة، تصلح للنادي أو المنزل.",
        "Full body · 3 days · bodyweight" to "الجسم كاملًا · 3 أيام · وزن الجسم",
        "Simple bodyweight plan: Monday, Wednesday, Friday. Needs little or no equipment." to "خطة بسيطة بوزن الجسم: الاثنين والأربعاء والجمعة، لا تحتاج إلا القليل من المعدات أو لا شيء.",
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
        "Full rest, then repeat the week. Move to the Beginner level once this feels easy." to "راحة تامة، ثم كرّر الأسبوع. انتقل إلى مستوى المبتدئين متى صار هذا سهلًا.",
    )

    private fun localize(p: PlanDraft): PlanDraft {
        if (java.util.Locale.getDefault().language != "ar") return p
        fun t(s: String) = arabic[s] ?: s
        return p.copy(name = t(p.name), description = t(p.description), days = p.days.map { it.copy(title = t(it.title), note = t(it.note)) })
    }

    /** In the reader's language; applied plans keep the names they were saved with. */
    val all: List<PlanDraft> get() = listOf(hemoFoundation, fullBodyBeginner, fullBodyDumbbells, pushPullLegs).map(::localize)

    /** Positions in [all], for onboarding's equipment ↔ plan matching. */
    const val BODYWEIGHT_INDEX = 1
    const val DUMBBELLS_INDEX = 2
    const val GYM_SPLIT_INDEX = 3
}
