import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import { db } from './db'
import { deleteMeasurement, logMeasurement, measurementTrend, normalizeMeasurementCm } from './measurements'

beforeEach(async () => {
  await db.bodyMeasurements.clear()
})

describe('measurements repository', () => {
  it('validates and rounds cm values', () => {
    expect(normalizeMeasurementCm(84.36)).toBe(84.4)
    expect(() => normalizeMeasurementCm(5)).toThrow()
    expect(() => normalizeMeasurementCm(350)).toThrow()
  })

  it('logs, updates same-day same-metric, sorts trend, and deletes', async () => {
    await logMeasurement('WAIST', 85.2, 20000)
    await logMeasurement('WAIST', 84.7, 20000)
    await logMeasurement('WAIST', 83.9, 20007)
    await logMeasurement('CHEST', 102.0, 20007)

    const all = await db.bodyMeasurements.toArray()
    expect(all).toHaveLength(3)

    const waist = measurementTrend(all, 'WAIST')
    expect(waist).toEqual([
      { epochDay: 20000, value: 84.7 },
      { epochDay: 20007, value: 83.9 },
    ])

    await deleteMeasurement(all[0].id!)
    expect(await db.bodyMeasurements.count()).toBe(2)
  })
})
