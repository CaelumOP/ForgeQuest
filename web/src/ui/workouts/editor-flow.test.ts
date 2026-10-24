import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import { db } from '../../data/db'
import { FULL_BODY_BEGINNER, localizePlan } from '../../data/seed/starter-plans'
import {
  activatePlan, addPlanItem, applyPlan, deletePlanItem, getActivePlan, movePlanItem, restorePlanItem, seedLibrary, swapPlanItem,
} from '../../data/workouts'

beforeEach(async () => {
  await Promise.all(db.tables.map((t) => t.clear()))
  await seedLibrary()
})

describe('plan editor flow on a starter plan', () => {
  it('opens, edits (move, swap, delete, restore, add) and activates', async () => {
    const id = await applyPlan(localizePlan(FULL_BODY_BEGINNER, 'en'), 'starter')
    const first = (await getActivePlan())!.days.find((d) => d.items.length >= 3)!
    const [a, b] = first.items
    await movePlanItem(a, 1)
    let day = (await getActivePlan())!.days.find((d) => d.day.id === first.day.id)!
    expect(day.items[1].id).toBe(a.id)
    expect(day.items[0].id).toBe(b.id)

    await swapPlanItem(b, 'crunch')
    day = (await getActivePlan())!.days.find((d) => d.day.id === first.day.id)!
    expect(day.items.find((i) => i.id === b.id)!.exerciseId).toBe('crunch')

    await deletePlanItem(a.id!)
    expect((await getActivePlan())!.days.find((d) => d.day.id === first.day.id)!.items.some((i) => i.id === a.id)).toBe(false)
    await restorePlanItem(a)
    expect((await getActivePlan())!.days.find((d) => d.day.id === first.day.id)!.items.some((i) => i.id === a.id)).toBe(true)

    const n = first.items.length
    await addPlanItem(first.day.id!, 'hip_bridge')
    day = (await getActivePlan())!.days.find((d) => d.day.id === first.day.id)!
    expect(day.items).toHaveLength(n + 1)

    const second = await applyPlan(localizePlan(FULL_BODY_BEGINNER, 'en'), 'starter')
    expect((await getActivePlan())!.plan.id).toBe(second)
    await activatePlan(id)
    expect((await getActivePlan())!.plan.id).toBe(id)
  })
})
