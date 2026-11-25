import 'fake-indexeddb/auto'
import fs from 'node:fs'
import path from 'node:path'
import { beforeEach, describe, expect, it } from 'vitest'
// Raw Android sources, read at test time so the web seed cannot drift from the Kotlin reference.
import exerciseAliasesKt from '../../../../app/src/main/java/com/forgequest/app/data/seed/ExerciseAliases.kt?raw'
import exerciseCatalogKt from '../../../../app/src/main/java/com/forgequest/app/data/seed/ExerciseCatalog.kt?raw'
import exerciseCuesKt from '../../../../app/src/main/java/com/forgequest/app/data/seed/ExerciseCues.kt?raw'
import exerciseLevelsKt from '../../../../app/src/main/java/com/forgequest/app/data/seed/ExerciseLevels.kt?raw'
import exerciseMediaKt from '../../../../app/src/main/java/com/forgequest/app/data/seed/ExerciseMedia.kt?raw'
import exerciseNamesArKt from '../../../../app/src/main/java/com/forgequest/app/data/seed/ExerciseNamesAr.kt?raw'
import starterPlansKt from '../../../../app/src/main/java/com/forgequest/app/data/seed/StarterPlans.kt?raw'
import arJson from '../../i18n/ar.json'
import enJson from '../../i18n/en.json'
import { MUSCLES } from '../../domain/muscle'
import { db } from '../db'
import { seedLibrary } from '../workouts'
import { EXERCISE_ALIASES } from './exercise-aliases'
import { CATALOG_METADATA_FIXES } from './catalog-fixes'
import { EXERCISE_CATALOG, STRETCH_IDS } from './exercise-catalog'
import { EXERCISE_CUE_KEYS } from './exercise-cues'
import { EXERCISE_LEVELS } from './exercise-levels'
import { EXERCISE_MEDIA_KEYS } from './exercise-media'
import { EXERCISE_NAMES_AR } from './exercise-names-ar'
import { filterExercisesBySearch, matchesExerciseSearch, normalizeExerciseSearch } from './search'
import { localizePlan, STARTER_PLANS, STARTER_PLANS_AR } from './starter-plans'

function extractKotlinMap(src: string, marker: string): Record<string, string> {
  const start = src.indexOf(marker)
  expect(start, marker).toBeGreaterThan(-1)
  const blockEnd = src.indexOf('\n    )', start)
  const block = src.slice(start, blockEnd > start ? blockEnd : undefined)
  const out: Record<string, string> = {}
  const re = /"([^"]+)"\s+to\s+"([^"]*)"/g
  let m: RegExpExecArray | null
  while ((m = re.exec(block)) !== null) {
    out[m[1]] = m[2]
  }
  return out
}

describe('seed data mirrors the Android catalog', () => {
  it('catalog ids are unique and match the number of ex(...) calls in ExerciseCatalog.kt', () => {
    const kotlinCount = (exerciseCatalogKt.match(/^\s+ex\("/gm) ?? []).length
    const ids = EXERCISE_CATALOG.map((e) => e.id)
    expect(new Set(ids).size).toBe(ids.length)
    expect(ids.length).toBe(kotlinCount)
    expect(ids.length).toBeGreaterThanOrEqual(800)
  })

  it('every catalog id has an Arabic name matching ExerciseNamesAr.kt', () => {
    const ids = new Set(EXERCISE_CATALOG.map((e) => e.id))
    const arIds = Object.keys(EXERCISE_NAMES_AR)
    const ktAr = extractKotlinMap(exerciseNamesArKt, 'val byId = mapOf(')
    expect(Object.keys(ktAr).length).toBe(ids.size)
    for (const id of ids) {
      expect(EXERCISE_NAMES_AR[id], id).toBeTruthy()
      expect(EXERCISE_NAMES_AR[id], id).toBe(ktAr[id])
    }
    for (const id of arIds) expect(ids.has(id), id).toBe(true)
  })

  it('cues match ExerciseCues.kt and exist in EN and AR i18n bundles', () => {
    const enStrings = enJson as Record<string, unknown>
    const arStrings = arJson as Record<string, unknown>
    for (const ex of EXERCISE_CATALOG) {
      const keys = EXERCISE_CUE_KEYS[ex.id]
      expect(keys, ex.id).toBeDefined()
      for (const k of keys) {
        expect(enStrings[k], `${ex.id}:${k}:en`).toBeTruthy()
        expect(arStrings[k], `${ex.id}:${k}:ar`).toBeTruthy()
      }
    }
    const addedCueMatches = exerciseCuesKt.match(/^\s+"([^"]+)"\s+to\s+Triple\(/gm) ?? []
    expect(addedCueMatches.length).toBeGreaterThanOrEqual(190)
  })

  it('mediaKeys match ExerciseMedia.kt and every mapped exercise has both _0.webp and _1.webp on Android and Web', () => {
    const ktMedia = extractKotlinMap(exerciseMediaKt, 'val byId: Map<String, String> = mapOf(')
    expect(Object.keys(EXERCISE_MEDIA_KEYS)).toEqual(Object.keys(ktMedia))

    const repoRoot = path.resolve(__dirname, '../../../..')
    const androidDir = path.join(repoRoot, 'app/src/main/assets/ex_media')
    const webDir = path.join(repoRoot, 'web/public/ex-media')

    let totalMediaBytes = 0
    for (const ex of EXERCISE_CATALOG) {
      expect(ex.mediaKey, ex.id).toBe(EXERCISE_MEDIA_KEYS[ex.id] ?? '')
      if (ex.mediaKey) {
        for (const frame of ['0', '1']) {
          const filename = `${ex.mediaKey}_${frame}.webp`
          const aPath = path.join(androidDir, filename)
          const wPath = path.join(webDir, filename)
          expect(fs.existsSync(aPath), `missing android ${filename}`).toBe(true)
          expect(fs.existsSync(wPath), `missing web ${filename}`).toBe(true)
          const size = fs.statSync(wPath).size
          expect(size, `ceiling ${filename}`).toBeLessThanOrEqual(20 * 1024)
          totalMediaBytes += size
        }
      }
    }
    expect(totalMediaBytes).toBeLessThanOrEqual(18 * 1024 * 1024)
  })

  it('levels match ExerciseLevels.kt and cover every mapped exercise with valid level strings', () => {
    const ktLevels = extractKotlinMap(exerciseLevelsKt, 'val byId: Map<String, String> = mapOf(')
    const valid = new Set(['beginner', 'intermediate', 'expert'])
    const ids = new Set(EXERCISE_CATALOG.map((e) => e.id))
    expect(Object.keys(EXERCISE_LEVELS).length).toBe(Object.keys(ktLevels).length)
    for (const [id, lvl] of Object.entries(ktLevels)) {
      expect(ids.has(id), id).toBe(true)
      expect(valid.has(lvl), `${id}:${lvl}`).toBe(true)
      expect(EXERCISE_LEVELS[id], id).toBe(lvl)
    }
    for (const ex of EXERCISE_CATALOG) {
      if (ex.mediaKey) expect(EXERCISE_LEVELS[ex.id], ex.id).toBeTruthy()
    }
  })

  it('all mobility stretches are registered in STRETCH_IDS', () => {
    // Neck strength holds sit in MOBILITY only so the plan builder skips them (plan audit); they are not stretches.
    const neckFixes = new Set(CATALOG_METADATA_FIXES.filter((f) => f.pattern === 'MOBILITY').map((f) => f.id))
    const mobilityIds = EXERCISE_CATALOG.filter((e) => e.pattern === 'MOBILITY' && !neckFixes.has(e.id)).map((e) => e.id)
    expect(mobilityIds.length).toBeGreaterThanOrEqual(100)
    for (const id of mobilityIds) {
      expect(STRETCH_IDS.has(id), id).toBe(true)
    }
  })

  it('aliases match ExerciseAliases.kt', () => {
    const ktAliases = extractKotlinMap(exerciseAliasesKt, 'val byId: Map<String, String> = mapOf(')
    expect(Object.keys(EXERCISE_ALIASES).length).toBe(Object.keys(ktAliases).length)
    for (const [id, val] of Object.entries(ktAliases)) {
      expect(EXERCISE_ALIASES[id], id).toBe(val)
    }
  })

  it('every muscle group has at least 2 primary exercises, and the new weak-muscle moves exist', () => {
    for (const m of MUSCLES) {
      expect(EXERCISE_CATALOG.filter((e) => e.primary.includes(m)).length, m).toBeGreaterThanOrEqual(2)
    }
    const adductors = EXERCISE_CATALOG.filter((e) => e.primary.includes('ADDUCTORS')).map((e) => e.id)
    expect(adductors).toEqual(expect.arrayContaining(['copenhagen_plank_short', 'copenhagen_plank_long', 'side_lying_hip_adduction', 'cossack_squat', 'band_hip_adduction']))
    const tibialis = EXERCISE_CATALOG.filter((e) => e.primary.includes('TIBIALIS')).map((e) => e.id)
    expect(tibialis).toEqual(expect.arrayContaining(['tibialis_raise', 'heel_walk', 'band_dorsiflexion']))
  })

  it('every exerciseId used in the starter plans exists in the catalog', () => {
    const ids = new Set(EXERCISE_CATALOG.map((e) => e.id))
    for (const plan of STARTER_PLANS) {
      for (const day of plan.days) {
        for (const item of day.items) expect(ids.has(item.exerciseId), `${plan.name}: ${item.exerciseId}`).toBe(true)
      }
    }
  })

  it('the number of starter plans equals the number of PlanDraft(...) in StarterPlans.kt', () => {
    const kotlinCount = (starterPlansKt.match(/val \w+ = PlanDraft\(/g) ?? []).length
    expect(STARTER_PLANS.length).toBe(kotlinCount)
  })

  it('the Arabic starter map has the same entry count as the Kotlin arabic map', () => {
    const start = starterPlansKt.indexOf('val arabic = mapOf(')
    const endMarkers = [starterPlansKt.indexOf('private val extraArabic', start), starterPlansKt.indexOf('private fun localize', start)].filter((i) => i > start)
    const block = starterPlansKt.slice(start, Math.min(...endMarkers))
    const kotlinCount = (block.match(/"\s*to\s*"/g) ?? []).length
    expect(Object.keys(STARTER_PLANS_AR).length).toBe(kotlinCount)
  })

  it('localizePlan translates the Hemo plan name to Arabic in ar and leaves it in en', () => {
    expect(localizePlan(STARTER_PLANS[0], 'ar').name).toBe('هيمو · المستوى التأسيسي')
    expect(localizePlan(STARTER_PLANS[0], 'en')).toBe(STARTER_PLANS[0])
  })
})

describe('exercise search', () => {
  it('normalizes tashkeel, hamza/alef, ta marbuta, and alef maksura', () => {
    expect(normalizeExerciseSearch('بَنْشُ بِرِس')).toBe('بنش برس')
    expect(normalizeExerciseSearch('إنكلاين')).toBe('انكلاين')
    expect(normalizeExerciseSearch('أعلى')).toBe('اعلي')
    expect(normalizeExerciseSearch('سمانة')).toBe('سمانه')
  })

  it('matches EN name, AR name, aliases, and id regardless of UI language', () => {
    const rdlAr = { ...EXERCISE_CATALOG.find((e) => e.id === 'romanian_deadlift')!, name: EXERCISE_NAMES_AR.romanian_deadlift }
    const benchEn = EXERCISE_CATALOG.find((e) => e.id === 'bench_press')!
    const calfEn = EXERCISE_CATALOG.find((e) => e.id === 'calf_raise')!
    const dbRowEn = EXERCISE_CATALOG.find((e) => e.id === 'db_row')!

    expect(matchesExerciseSearch(rdlAr, 'RDL')).toBe(true)
    expect(matchesExerciseSearch(rdlAr, 'Romanian Deadlift')).toBe(true)
    expect(matchesExerciseSearch(rdlAr, 'romanian_deadlift')).toBe(true)
    expect(matchesExerciseSearch(benchEn, 'بنش')).toBe(true)
    expect(matchesExerciseSearch(benchEn, 'بَنْش')).toBe(true)
    expect(matchesExerciseSearch(calfEn, 'سمانه')).toBe(true)
    expect(matchesExerciseSearch(dbRowEn, 'منشار')).toBe(true)

    const hits = filterExercisesBySearch(EXERCISE_CATALOG, 'OHP')
    expect(hits.some((e) => e.id === 'overhead_press')).toBe(true)
  })
})

describe('seedLibrary upgrade and idempotency', () => {
  beforeEach(async () => {
    await db.delete()
    await db.open()
  })

  it('upgrades existing 4.0 rows, preserves custom & user renames/notes, and is idempotent', async () => {
    await db.exercises.bulkPut([
      {
        id: 'burpee',
        name: 'البيربي', // historical Arabic name
        kind: 'REPS',
        primary: ['QUADS', 'CHEST'],
        secondary: ['SHOULDERS', 'ABS'],
        videoUrl: null,
        custom: false,
        equipment: '',
        pattern: '',
        setupNote: 'Keep steady pace',
        aliases: '',
        unitOverride: '',
        mediaKey: '',
        pinned: true,
      },
      {
        id: 'bench_press',
        name: 'My Custom Bench', // user renamed built-in
        kind: 'WEIGHTED',
        primary: ['CHEST'],
        secondary: ['TRICEPS', 'SHOULDERS'],
        videoUrl: 'https://example.com/my-video',
        custom: false,
        equipment: '',
        pattern: '',
        setupNote: 'Pin 4',
        aliases: '',
        unitOverride: 'LB',
        mediaKey: '',
        pinned: false,
      },
      {
        id: 'custom_jammer',
        name: 'Jammer Press',
        kind: 'WEIGHTED',
        primary: ['CHEST'],
        secondary: ['SHOULDERS'],
        videoUrl: null,
        custom: true,
        equipment: 'MACHINE',
        pattern: 'HORIZONTAL_PUSH',
        setupNote: 'Seat 2',
        aliases: 'jammer',
        unitOverride: '',
        mediaKey: '',
        pinned: true,
      },
    ])

    await seedLibrary('ar')

    expect(await db.exercises.count()).toBe(EXERCISE_CATALOG.length + 1)

    const burpee = (await db.exercises.get('burpee'))!
    expect(burpee.name).toBe('بيربي')
    expect(burpee.equipment).toBe('BODYWEIGHT')
    expect(burpee.aliases).toContain('البيربي')
    expect(burpee.setupNote).toBe('Keep steady pace')
    expect(burpee.pinned).toBe(true)

    const bench = (await db.exercises.get('bench_press'))!
    expect(bench.name).toBe('My Custom Bench') // user rename preserved
    expect(bench.videoUrl).toBe('https://example.com/my-video') // user video preserved
    expect(bench.equipment).toBeTruthy() // empty metadata backfilled
    expect(bench.mediaKey).toBe('bench_press')
    expect(bench.setupNote).toBe('Pin 4')
    expect(bench.unitOverride).toBe('LB')

    const custom = (await db.exercises.get('custom_jammer'))!
    expect(custom.name).toBe('Jammer Press')
    expect(custom.setupNote).toBe('Seat 2')

    // Idempotent second run
    await seedLibrary('ar')
    expect(await db.exercises.count()).toBe(EXERCISE_CATALOG.length + 1)
    expect((await db.exercises.get('bench_press'))!.name).toBe('My Custom Bench')
  })

  it('applies catalog fixes to pre-fix built-in rows on seedLibrary while leaving user-edited and custom rows alone', async () => {
    await db.exercises.bulkPut([
      {
        id: 'sled_overhead_backward_walk', name: 'Sled Overhead Backward Walk', kind: 'REPS',
        primary: ['SHOULDERS'], secondary: ['CALVES', 'UPPER_BACK', 'QUADS'], videoUrl: null,
        custom: false, equipment: 'BODYWEIGHT', pattern: 'VERTICAL_PUSH',
      },
      {
        id: 'kipping_muscle_up', name: 'Kipping Muscle Up', kind: 'REPS',
        primary: ['UPPER_BACK'], secondary: ['ABS', 'BICEPS'], videoUrl: null,
        custom: false, equipment: 'BODYWEIGHT', pattern: 'HORIZONTAL_PULL',
      },
      {
        id: 'db_bench_press', name: 'Dumbbell bench press', kind: 'WEIGHTED',
        primary: ['CHEST'], secondary: ['TRICEPS'], videoUrl: null,
        custom: false, equipment: 'DUMBBELLS,FLAT_BENCH', pattern: 'HORIZONTAL_PUSH',
      },
      {
        // User changed equipment from BODYWEIGHT to CABLE_MACHINE, so equipment stays CABLE_MACHINE.
        id: 'sled_row', name: 'Sled Row', kind: 'REPS',
        primary: ['UPPER_BACK'], secondary: ['BICEPS'], videoUrl: null,
        custom: false, equipment: 'CABLE_MACHINE', pattern: 'HORIZONTAL_PULL',
      },
      {
        // Custom row with same id must never be touched.
        id: 'sled_push', name: 'Custom Sled', kind: 'HOLD',
        primary: ['QUADS'], secondary: [], videoUrl: null,
        custom: true, equipment: 'BODYWEIGHT', pattern: 'CARRY_CONDITIONING',
      },
    ])

    await seedLibrary('en')

    const walk = (await db.exercises.get('sled_overhead_backward_walk'))!
    expect(walk.equipment).toBe('MACHINE')
    expect(walk.pattern).toBe('CARRY_CONDITIONING')

    const muscleUp = (await db.exercises.get('kipping_muscle_up'))!
    expect(muscleUp.equipment).toBe('PULL_UP_BAR')
    expect(muscleUp.pattern).toBe('VERTICAL_PULL')

    const dbBench = (await db.exercises.get('db_bench_press'))!
    expect(dbBench.secondary).toEqual(['TRICEPS', 'SHOULDERS'])

    const sledRow = (await db.exercises.get('sled_row'))!
    expect(sledRow.equipment).toBe('CABLE_MACHINE')

    const sledPush = (await db.exercises.get('sled_push'))!
    expect(sledPush.equipment).toBe('BODYWEIGHT')
    expect(sledPush.custom).toBe(true)
  })

  it('ranks bench_press above Bench Jump and Bench Press with Chains for "bench"', async () => {
    const { filterExercisesBySearch } = await import('./search')
    const sorted = [...EXERCISE_CATALOG].sort((a, b) => (a.name < b.name ? -1 : a.name > b.name ? 1 : 0))
    const hits = filterExercisesBySearch(sorted, 'bench')
    expect(hits[0]?.id).toBe('bench_press')
  })
})
