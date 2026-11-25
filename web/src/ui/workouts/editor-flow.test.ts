import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import { tIn } from '../../i18n'
import { db } from '../../data/db'
import { FULL_BODY_BEGINNER, localizePlan } from '../../data/seed/starter-plans'
import {
  activatePlan, addPlanItem, applyPlan, deletePlanItem, getActivePlan, movePlanItem, restorePlanItem, seedLibrary, swapPlanItem,
} from '../../data/workouts'
import { isPairedWithNext } from '../../domain/plan-share'
import { savePlanItemGrouping } from './helpers'

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

  it('groups with next (matching differing sets first) and clears circuit/pairing when turned off', async () => {
    expect(tIn('en', 'plan41_group_next')).toBe('Group with next')
    expect(tIn('ar', 'plan41_group_next')).toBe('اجمعه مع التالي')

    await applyPlan(localizePlan(FULL_BODY_BEGINNER, 'en'), 'starter')
    let day = (await getActivePlan())!.days.find((d) => d.items.length >= 3)!
    const first = day.items[0]!
    const second = day.items[1]!

    // Differing set counts: saving updates next item's sets to match first item's sets so the pair is valid.
    await savePlanItemGrouping(day.day.id, { ...first, sets: 4 }, second, true, true)
    day = (await getActivePlan())!.days.find((d) => d.day.id === day.day.id)!
    expect(day.items[0]!.sets).toBe(4)
    expect(day.items[1]!.sets).toBe(4)
    expect(isPairedWithNext(day.items, 0)).toBe(true)

    // Turning off unpairs.
    await savePlanItemGrouping(day.day.id, day.items[0]!, day.items[1]!, false, true)
    day = (await getActivePlan())!.days.find((d) => d.day.id === day.day.id)!
    expect(isPairedWithNext(day.items, 0)).toBe(false)
    expect(day.items[0]!.supersetGroup).toBeNull()

    // Circuit item kept ON preserves circuit without forcing supersetGroup; turning OFF clears circuit and pairing.
    await savePlanItemGrouping(day.day.id, { ...day.items[0]!, circuit: true }, day.items[1]!, true, true)
    day = (await getActivePlan())!.days.find((d) => d.day.id === day.day.id)!
    expect(day.items[0]!.circuit).toBe(true)
    expect(day.items[0]!.supersetGroup).toBeNull()

    await savePlanItemGrouping(day.day.id, { ...day.items[0]!, circuit: false }, day.items[1]!, false, true)
    day = (await getActivePlan())!.days.find((d) => d.day.id === day.day.id)!
    expect(day.items[0]!.circuit).toBe(false)
    expect(day.items[0]!.supersetGroup).toBeNull()

    // No next non-extra item (showGroup = false): saves item without pairing.
    const lastIdx = day.items.length - 1
    await savePlanItemGrouping(day.day.id, { ...day.items[lastIdx]!, sets: 5 }, undefined, true, false)
    day = (await getActivePlan())!.days.find((d) => d.day.id === day.day.id)!
    expect(day.items[lastIdx]!.sets).toBe(5)
    expect(day.items[lastIdx]!.supersetGroup).toBeNull()
  })
})
