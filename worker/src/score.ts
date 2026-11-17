import type { PrItem, SnapshotInput, SnapshotRow } from "./types";

export function scoreBreakdown(done: number, target: number, prCount = 0, proteinDays = 0) {
  const base = target === 0 ? 0 : Math.round(100 * Math.min(done, target) / target);
  const prBonus = Math.min(prCount * 5, 10);
  const proteinBonus = Math.min(proteinDays * 2, 6);
  const extraBonus = done > target ? 5 : 0;
  const bonus = Math.min(20, prBonus + proteinBonus + extraBonus);
  return { base, prBonus, proteinBonus, extraBonus, bonus, score: base + bonus };
}

/** The existing PR JSON column accepts old arrays and a versioned envelope; no schema migration. */
export interface ScoreEnvelope {
  scoreVersion: 1 | 2;
  target: number;
  prCount: number;
  reachedAt: number;
  prs: PrItem[] | null;
}

export function readScore(row: SnapshotRow): ScoreEnvelope {
  let raw: unknown = null;
  try { raw = row.prs === null ? null : JSON.parse(row.prs); } catch { /* old corrupt PR details */ }
  if (raw && !Array.isArray(raw) && typeof raw === "object" && "scoreVersion" in raw) {
    return raw as ScoreEnvelope;
  }
  return { scoreVersion: 1, target: row.due, prCount: 0, reachedAt: row.updated_at,
    prs: Array.isArray(raw) ? raw as PrItem[] : null };
}

export function snapshotScore(s: Pick<SnapshotInput, "done" | "due" | "target" | "prCount" | "proteinDays">) {
  if (s.target === undefined) {
    // 4.0 clients' rule (ForgeScore.kt on 4.0): nothing due yet this week = on track (100), else done/due.
    const base = s.due === 0 ? 100 : Math.round(100 * Math.min(s.done, s.due) / s.due);
    return { base, prBonus: 0, proteinBonus: 0, extraBonus: 0, bonus: 0, score: base };
  }
  return scoreBreakdown(s.done, s.target, s.prCount ?? 0, s.proteinDays);
}

export function encodeScore(s: SnapshotInput, nowSec: number): string {
  return JSON.stringify({ scoreVersion: s.target === undefined ? 1 : 2, target: s.target ?? s.due,
    prCount: s.prCount ?? 0, reachedAt: s.reachedAt ?? nowSec, prs: s.prs } satisfies ScoreEnvelope);
}
