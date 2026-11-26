import 'fake-indexeddb/auto'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { db, type BaselineTestRow, type ExerciseRow, type SetRow } from '../../data/db'
import { PULL_UP_ID, PUSH_UP_ID, saveBaselineTest } from '../../data/baseline-tests'
import { EXERCISE_CATALOG } from '../../data/seed/exercise-catalog'
import { DEFAULT_SETTINGS, type Settings } from '../../data/settings'
import type { ActivePlan } from '../../data/workouts'
import { formatWeight } from '../../domain/units'
import { setLang } from '../../i18n'
import {
  BASELINE_REST_KEY,
  buildBaselineMoves,
  computeTargetDisplay,
  formatLastResult,
  formatTargetText,
  loadBaselineRest,
  PLANK_ID,
  saveBaselineRest,
  shouldShowBaselineEntry,
  type RestStore,
} from './helpers'

const catalog: ExerciseRow[] = EXERCISE_CATALOG.map((e) => ({
  ...e,
  videoUrl: e.videoUrl ?? null,
  custom: false,
}))

const settingsWith = (patch: Partial<Settings> = {}): Settings => ({
  ...DEFAULT_SETTINGS,
  ...patch,
})

const memoryStore = (): RestStore & { data: Map<string, string> } => {
  const data = new Map<string, string>()
  return {
    data,
    getItem: (k) => data.get(k) ?? null,
    setItem: (k, v) => { data.set(k, v) },
    removeItem: (k) => { data.delete(k) },
  }
}

beforeEach(async () => {
  await Promise.all(db.tables.map((t) => t.clear()))
  setLang('en')
})

afterEach(() => {
  setLang('en')
  vi.restoreAllMocks()
})

describe('baseline helpers', () => {
  it('gates pull-up by PULL_UP_BAR equipment and includes first 3 main weighted lifts from active plan', () => {
    const plan: ActivePlan = {
      plan: { id: 1, name: 'Test', description: '', source: 'builder', active: true, createdAt: 1 },
      days: [
        {
          day: { id: 10, planId: 1, dayOfWeek: 1, title: 'Day 1', isRest: false, note: '' },
          items: [
            { id: 1, dayId: 10, position: 0, exerciseId: 'jumping_jacks', block: 'Warm-up', circuit: false, sets: 2, repsMin: 20, repsMax: null, seconds: null, weightKg: null, restSeconds: 30, note: '' },
            { id: 2, dayId: 10, position: 1, exerciseId: 'goblet_squat', block: 'Main', circuit: false, sets: 3, repsMin: 10, repsMax: 12, seconds: null, weightKg: null, restSeconds: 90, note: '' },
            { id: 3, dayId: 10, position: 2, exerciseId: 'db_bench_press', block: 'Main', circuit: false, sets: 3, repsMin: 6, repsMax: 8, seconds: null, weightKg: null, restSeconds: 90, note: '' },
          ],
        },
        {
          day: { id: 11, planId: 1, dayOfWeek: 3, title: 'Day 2', isRest: false, note: '' },
          items: [
            { id: 4, dayId: 11, position: 0, exerciseId: 'goblet_squat', block: 'Main', circuit: false, sets: 3, repsMin: 8, repsMax: 10, seconds: null, weightKg: null, restSeconds: 90, note: '' },
            { id: 5, dayId: 11, position: 1, exerciseId: 'romanian_deadlift', block: 'Main', circuit: false, sets: 3, repsMin: 8, repsMax: 10, seconds: null, weightKg: null, restSeconds: 90, note: '' },
            { id: 6, dayId: 11, position: 2, exerciseId: 'overhead_press', block: 'Main', circuit: false, sets: 3, repsMin: 8, repsMax: 10, seconds: null, weightKg: null, restSeconds: 90, note: '' },
          ],
        },
      ],
    }

    const noBar = buildBaselineMoves(catalog, settingsWith({ equipment: 'DUMBBELLS' }), plan, new Map())
    expect(noBar.map((m) => m.exercise.id)).toEqual([
      PUSH_UP_ID,
      PLANK_ID,
      'goblet_squat',
      'db_bench_press',
      'romanian_deadlift',
    ])
    expect(noBar.find((m) => m.exercise.id === 'db_bench_press')?.repsMin).toBe(6)

    const withBar = buildBaselineMoves(catalog, settingsWith({ equipment: 'DUMBBELLS,PULL_UP_BAR' }), plan, new Map())
    expect(withBar.map((m) => m.exercise.id)).toEqual([
      PUSH_UP_ID,
      PULL_UP_ID,
      PLANK_ID,
      'goblet_squat',
      'db_bench_press',
      'romanian_deadlift',
    ])
  })

  it('appends extra tested moves newest first and ignores mismatched kind rows', () => {
    const latest = new Map<string, BaselineTestRow>([
      ['barbell_row', { id: 1, exerciseId: 'barbell_row', kind: 'WEIGHTED', reps: 8, seconds: null, weightKg: 50, createdAt: 1000 }],
      ['overhead_press', { id: 2, exerciseId: 'overhead_press', kind: 'WEIGHTED', reps: 8, seconds: null, weightKg: 40, createdAt: 2000 }],
      [PUSH_UP_ID, { id: 3, exerciseId: PUSH_UP_ID, kind: 'HOLD', reps: null, seconds: 20, weightKg: null, createdAt: 3000 }],
    ])
    const moves = buildBaselineMoves(catalog, settingsWith({ equipment: 'BARBELL' }), null, latest)
    expect(moves.map((m) => m.exercise.id)).toEqual([PUSH_UP_ID, PLANK_ID, 'overhead_press', 'barbell_row'])
    expect(moves[0]?.latest).toBeNull()
    expect(moves[2]?.isSuggested).toBe(false)
  })

  it('computes REPS, HOLD, WEIGHTED, 0-rep easier variation, and too-light targets', () => {
    const pushUp = catalog.find((e) => e.id === PUSH_UP_ID)!
    const plank = catalog.find((e) => e.id === PLANK_ID)!
    const bench = catalog.find((e) => e.id === 'db_bench_press')!
    const eq = new Set(['BODYWEIGHT', 'DUMBBELLS'] as const)

    const repsOk = computeTargetDisplay(pushUp, { reps: 12, seconds: null, weightKg: null }, catalog, eq, 'KG')
    expect(repsOk).toEqual({ kind: 'REPS', reps: 8 })

    const repsZero = computeTargetDisplay(pushUp, { reps: 0, seconds: null, weightKg: null }, catalog, eq, 'KG')
    expect(repsZero.kind).toBe('EASIER_VARIATION')
    if (repsZero.kind === 'EASIER_VARIATION') {
      expect(repsZero.easierExercise).not.toBeNull()
      expect(repsZero.easierExercise?.id).not.toBe(PUSH_UP_ID)
    }

    expect(computeTargetDisplay(plank, { reps: null, seconds: 45, weightKg: null }, catalog, eq, 'KG')).toEqual({
      kind: 'HOLD',
      seconds: 30,
    })
    expect(computeTargetDisplay(plank, { reps: null, seconds: 0, weightKg: null }, catalog, eq, 'KG')).toEqual({
      kind: 'HOLD_TOO_SHORT',
    })

    const wOk = computeTargetDisplay(bench, { reps: 8, seconds: null, weightKg: 40 }, catalog, eq, 'KG', 8)
    expect(wOk.kind).toBe('WEIGHT')
    expect(computeTargetDisplay(bench, { reps: 15, seconds: null, weightKg: 20 }, catalog, eq, 'KG', 8)).toEqual({
      kind: 'TOO_LIGHT',
    })
    expect(computeTargetDisplay(bench, { reps: 5, seconds: null, weightKg: 1 }, catalog, eq, 'KG', 8)).toEqual({
      kind: 'WEIGHT_NO_TARGET',
    })
  })

  it('formats last results and target labels in EN and AR', () => {
    setLang('en')
    expect(formatLastResult({ reps: 12, seconds: null, weightKg: null }, 'REPS', 'KG', 'en')).toBe('Last: 12 reps')
    expect(formatTargetText({ kind: 'REPS', reps: 8 }, 'en')).toBe('Start with 8 reps')
    expect(formatTargetText({ kind: 'HOLD', seconds: 30 }, 'en')).toBe('Start with 30 s')
    expect(formatTargetText({ kind: 'WEIGHT', weightKg: 22.5, unit: 'KG' }, 'en')).toBe(
      `Start with ${formatWeight('KG', 22.5, false)}`,
    )
    expect(formatTargetText({ kind: 'TOO_LIGHT' }, 'en')).toBe('Too light to judge: try a heavier weight')

    setLang('ar')
    expect(formatTargetText({ kind: 'HOLD', seconds: 30 }, 'ar')).toBe('ابدأ بـ 30 ث')
    expect(formatTargetText({ kind: 'TOO_LIGHT' }, 'ar')).toBe('الوزن خفيف جدًا للحكم: جرّب وزنًا أثقل')
  })

  it('shows workouts entry card only when no finished workout, no baseline row, and not dismissed', () => {
    expect(shouldShowBaselineEntry(false, false, false)).toBe(true)
    expect(shouldShowBaselineEntry(true, false, false)).toBe(false)
    expect(shouldShowBaselineEntry(false, true, false)).toBe(false)
    expect(shouldShowBaselineEntry(false, false, true)).toBe(false)
  })

  it('persists and expires 90s rest end time from Date.now()', () => {
    const store = memoryStore()
    saveBaselineRest(store, 100_000)
    expect(store.data.get(BASELINE_REST_KEY)).toBe('100000')
    expect(loadBaselineRest(store, 50_000)).toBe(100_000)
    expect(loadBaselineRest(store, 100_000)).toBeNull()
    expect(store.data.has(BASELINE_REST_KEY)).toBe(false)
  })

  it('saving baseline tests never creates workout sessions or sets', async () => {
    await saveBaselineTest({ exerciseId: PUSH_UP_ID, kind: 'REPS', reps: 15, createdAt: 1000 })
    await saveBaselineTest({ exerciseId: PLANK_ID, kind: 'HOLD', seconds: 45, createdAt: 2000 })
    await saveBaselineTest({ exerciseId: 'db_bench_press', kind: 'WEIGHTED', weightKg: 30, reps: 8, createdAt: 3000 })
    expect(await db.baselineTests.count()).toBe(3)
    expect(await db.sessions.count()).toBe(0)
    expect(await db.sets.count()).toBe(0)
  })
})

describe('SetRowView blank weighted placeholder and Test this move button', () => {
  it('renders Add weight placeholder and Test this move button for blank weighted sets, and navigates to #/baseline/<id>', async () => {
    class FakeNode {
      childNodes: FakeNode[] = []
      parentNode: FakeNode | null = null
      attributes: Record<string, string> = {}
      listeners: Record<string, Array<(e?: unknown) => void>> = {}
      style: Record<string, unknown> & { setProperty: (k: string, v: string) => void } = { setProperty() {} }
      data = ''
      constructor(public nodeType: number, public nodeName: string) {}
      get localName() { return this.nodeName.toLowerCase() }
      get firstChild() { return this.childNodes[0] ?? null }
      get nextSibling() {
        const sibs = this.parentNode?.childNodes ?? []
        return sibs[sibs.indexOf(this) + 1] ?? null
      }
      get textContent(): string { return this.nodeType === 3 ? this.data : this.childNodes.map((c) => c.textContent).join('') }
      set textContent(v: string) { this.childNodes = []; if (v) this.appendChild(fakeDoc.createTextNode(v)) }
      insertBefore(n: FakeNode, ref: FakeNode | null) {
        n.parentNode?.removeChild(n)
        const i = ref ? this.childNodes.indexOf(ref) : -1
        if (i < 0) this.childNodes.push(n); else this.childNodes.splice(i, 0, n)
        n.parentNode = this
        return n
      }
      appendChild(n: FakeNode) { return this.insertBefore(n, null) }
      removeChild(n: FakeNode) { this.childNodes = this.childNodes.filter((c) => c !== n); n.parentNode = null; return n }
      remove() { this.parentNode?.removeChild(this) }
      setAttribute(k: string, v: string) { this.attributes[k] = String(v) }
      removeAttribute(k: string) { delete this.attributes[k] }
      getAttribute(k: string) { return this.attributes[k] ?? null }
      addEventListener(type: string, fn: (e?: unknown) => void) {
        (this.listeners[type] ??= []).push(fn)
      }
      removeEventListener(type: string, fn: (e?: unknown) => void) {
        this.listeners[type] = (this.listeners[type] ?? []).filter((f) => f !== fn)
      }
      click() {
        const evt = { type: 'click', target: this, currentTarget: this, preventDefault() {}, stopPropagation() {} }
        ;(this.listeners.click ?? []).forEach((fn) => fn.call(this, evt))
      }
    }
    const fakeDoc = {
      createElement: (tag: string) => new FakeNode(1, tag.toUpperCase()),
      createElementNS: (_ns: string, tag: string) => new FakeNode(1, tag.toUpperCase()),
      createTextNode: (text: string) => Object.assign(new FakeNode(3, '#text'), { data: text }),
      documentElement: new FakeNode(1, 'HTML'),
      addEventListener() {},
      removeEventListener() {},
    }
    const fakeLocation = { hash: '#/live' }
    vi.stubGlobal('document', fakeDoc)
    vi.stubGlobal('location', fakeLocation)

    const { h, render } = await import('preact')
    const { SetRowView } = await import('../live/SetRowView')

    const findButtons = (n: FakeNode): FakeNode[] => {
      const out: FakeNode[] = n.nodeName === 'BUTTON' ? [n] : []
      for (const c of n.childNodes) out.push(...findButtons(c))
      return out
    }

    const blankSet: SetRow = {
      id: 1, sessionId: 1, exerciseId: 'dumbbell_bench_press', itemPosition: 0, block: 'Main',
      circuit: false, setIndex: 0, targetLabel: '8-12 reps', reps: 8, seconds: null, weightKg: 0,
      done: false, isPr: false, restSeconds: 90, note: '', effort: '', doneAt: null, setType: 'WORKING',
    }
    const root = new FakeNode(1, 'DIV')
    render(
      h(SetRowView, {
        label: '1',
        sublabel: null,
        set: blankSet,
        kind: 'WEIGHTED',
        unit: 'KG',
        showEffort: false,
        onChange: () => {},
        onToggle: () => {},
        onSetEffort: () => {},
        onStartHold: () => {},
      }),
      root as unknown as Element,
    )

    expect(root.textContent).toContain('Add weight')
    expect(root.textContent).not.toContain('0 kg')
    expect(root.textContent).toContain('Test this move')

    const testBtn = findButtons(root).find((b) => b.textContent.includes('Test this move'))
    expect(testBtn).toBeDefined()
    testBtn!.click()
    expect(fakeLocation.hash).toBe('#/baseline/dumbbell_bench_press')

    // Once weighted set has a weight > 0, neither "Add weight" nor "Test this move" renders.
    const rootLoaded = new FakeNode(1, 'DIV')
    render(
      h(SetRowView, {
        label: '1',
        sublabel: null,
        set: { ...blankSet, weightKg: 20 },
        kind: 'WEIGHTED',
        unit: 'KG',
        showEffort: false,
        onChange: () => {},
        onToggle: () => {},
        onSetEffort: () => {},
        onStartHold: () => {},
      }),
      rootLoaded as unknown as Element,
    )
    expect(rootLoaded.textContent).toContain('20 kg')
    expect(rootLoaded.textContent).not.toContain('Add weight')
    expect(rootLoaded.textContent).not.toContain('Test this move')
  })
})
