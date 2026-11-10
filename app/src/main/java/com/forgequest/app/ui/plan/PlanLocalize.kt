package com.forgequest.app.ui.plan

import com.forgequest.app.AppContainer
import com.forgequest.app.R
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.domain.BuiltPlan
import com.forgequest.app.domain.TrainGoal

/** The built plan as a draft with its name, description, day titles and notes written in the app language. */
internal fun localizeBuiltPlan(app: AppContainer, plan: BuiltPlan, goal: TrainGoal): PlanDraft {
    val days = plan.draft.days.map { d ->
        d.copy(
            title = plan.days[d.dayOfWeek]?.let { app.str(it.pbRes()) } ?: d.title,
            note = plan.why[d.dayOfWeek].orEmpty().joinToString(" ") { app.str(it.pbRes()) },
        )
    }
    return plan.draft.copy(
        name = app.str(R.string.pb_plan_name, app.str(goal.pbRes())),
        description = app.str(R.string.pb_progression),
        days = days,
    )
}
