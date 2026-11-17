import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import strongCsv from './fixtures/strong_sample.csv?raw'
import hevyCsv from './fixtures/hevy_sample.csv?raw'
import fitnotesCsv from './fixtures/fitnotes_sample.csv?raw'
import { db } from '../db'
import { EXERCISE_CATALOG } from '../seed/exercise-catalog'
import { epochDayOf } from '../../domain/dates'
import { executeCsvImport, matchCsvExercises, parseCsvRows, parseEpochDay, previewCsvImport } from './csvImport'

beforeEach(async () => {
  await Promise.all(db.tables.map((t) => t.clear()))
  await db.exercises.bulkPut(
    EXERCISE_CATALOG.map((e) => ({
      ...e,
      videoUrl: e.videoUrl ?? null,
      custom: e.custom ?? false,
      equipment: e.equipment ?? '',
      pattern: e.pattern ?? '',
      setupNote: e.setupNote ?? '',
      aliases: e.aliases ?? '',
      unitOverride: e.unitOverride ?? '',
      pinned: e.pinned ?? false,
      mediaKey: e.mediaKey ?? '',
    })),
  )
})

describe('CSV import', () => {
  it('parses Strong, Hevy, and FitNotes fixture exports', () => {
    const strong = parseCsvRows(strongCsv)
    expect(strong.source).toBe('STRONG')
    expect(strong.rows).toHaveLength(4)
    expect(strong.rows[0].setType).toBe('WARMUP')
    expect(strong.rows[1].weightKg).toBe(80)

    const hevy = parseCsvRows(hevyCsv)
    expect(hevy.source).toBe('HEVY')
    expect(hevy.rows).toHaveLength(4)
    expect(hevy.rows[0].setType).toBe('WARMUP')
    expect(hevy.rows[2].setType).toBe('DROPSET')
    expect(hevy.rows[3].setType).toBe('FAILURE')

    const fit = parseCsvRows(fitnotesCsv)
    expect(fit.source).toBe('FITNOTES')
    expect(fit.rows).toHaveLength(4)
    expect(fit.rows[2].seconds).toBe(60)
    expect(fit.rows[3].weightKg).toBeCloseTo(135 / 2.2046225, 1)
  })

  it('parses calendar dates timezone-safely', () => {
    const expected = epochDayOf(2026, 11, 10)
    expect(parseEpochDay('2026-11-10 00:05:00')).toBe(expected)
    expect(parseEpochDay('2026-11-10 23:55:00')).toBe(expected)
    expect(parseEpochDay('10 Nov 2026, 17:00')).toBe(expected)
  })

  it('matches known exercises and flags unmatched for preview', async () => {
    const lib = await db.exercises.toArray()
    const matches = matchCsvExercises(['Bench Press (Barbell)', 'Lat Pulldown (Cable)', 'Custom Cable Fly'], lib)
    expect(matches.get('Bench Press (Barbell)')?.matchedId).toBe('bench_press')
    expect(matches.get('Lat Pulldown (Cable)')?.matchedId).toBe('lat_pulldown')
    expect(matches.get('Custom Cable Fly')?.status).toBe('UNMATCHED')
  })

  it('imports transactionally with custom mapping and dedupes re-imports', async () => {
    const preview = await previewCsvImport(strongCsv)
    expect(preview.totalWorkouts).toBe(1)
    expect(preview.newSetsCount).toBe(4)

    const first = await executeCsvImport(
      preview,
      new Map([['Custom Cable Fly', { kind: 'create', name: 'Custom Cable Fly' }]]),
    )
    expect(first).toEqual({ importedWorkouts: 1, importedSets: 4, skippedDuplicates: 0 })
    expect((await db.exercises.toArray()).some((e) => e.custom && e.name === 'Custom Cable Fly')).toBe(true)

    const preview2 = await previewCsvImport(strongCsv)
    expect(preview2.duplicateSetsCount).toBe(4)
    const second = await executeCsvImport(preview2, new Map())
    expect(second).toEqual({ importedWorkouts: 0, importedSets: 0, skippedDuplicates: 4 })
  })
})
