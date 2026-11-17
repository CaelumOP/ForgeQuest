import { db, type BodyMeasurementRow } from './db'
import { useLive } from './live'
import { todayEpochDay } from '../domain/dates'

export const MEASUREMENT_METRICS = ['WAIST', 'CHEST', 'ARMS', 'THIGHS', 'HIPS'] as const
export type MeasurementMetric = (typeof MEASUREMENT_METRICS)[number]

export const MIN_MEASUREMENT_CM = 10
export const MAX_MEASUREMENT_CM = 300

export function normalizeMeasurementCm(valueCm: number): number {
  if (!Number.isFinite(valueCm) || valueCm < MIN_MEASUREMENT_CM || valueCm > MAX_MEASUREMENT_CM) {
    throw new RangeError(`Measurement must be between ${MIN_MEASUREMENT_CM} and ${MAX_MEASUREMENT_CM} cm`)
  }
  return Math.round(valueCm * 10) / 10
}

export function measurementTrend(
  rows: readonly BodyMeasurementRow[],
  metric: MeasurementMetric,
): { epochDay: number; value: number }[] {
  return rows
    .filter((r) => r.metric === metric)
    .sort((a, b) => a.epochDay - b.epochDay || a.loggedAt - b.loggedAt)
    .map((r) => ({ epochDay: r.epochDay, value: r.valueCm }))
}

export async function logMeasurement(
  metric: MeasurementMetric,
  valueCm: number,
  epochDay = todayEpochDay(),
): Promise<number> {
  const clean = normalizeMeasurementCm(valueCm)
  const existing = await db.bodyMeasurements
    .where('epochDay')
    .equals(epochDay)
    .filter((r) => r.metric === metric)
    .first()
  const row: BodyMeasurementRow = {
    ...(existing?.id != null ? { id: existing.id } : {}),
    epochDay,
    metric,
    valueCm: clean,
    loggedAt: Date.now(),
  }
  return db.bodyMeasurements.put(row)
}

export async function deleteMeasurement(id: number): Promise<void> {
  await db.bodyMeasurements.delete(id)
}

export function useMeasurements(): BodyMeasurementRow[] {
  return useLive(() => db.bodyMeasurements.orderBy('epochDay').toArray(), [], [] as BodyMeasurementRow[])
}

/** Whether at least one body measurement has been logged. */
export const hasAnyMeasurement = async (): Promise<boolean> => (await db.bodyMeasurements.count()) > 0
