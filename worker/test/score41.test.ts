import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { toSnapshot } from "../src/types";
import { scoreBreakdown } from "../src/score";
import { validateSnapshot } from "../src/validate";
const vectors = JSON.parse(readFileSync(new URL("./forge-score-v2.vectors.json", import.meta.url), "utf8"));
describe("ForgeScore v2", () => {
  for (const v of vectors.cases) it(v.name, () => {
    expect(scoreBreakdown(v.done, v.target, v.prCount, v.proteinDays)).toEqual(v.expect);
  });
  const old = { weekStart: 20772, done: 2, due: 3, score: 100, badge: "trained", streakWeeks: 1, proteinDays: 7, prs: null };
  it("recalculates rows persisted before v2 without rewriting old PR arrays", () => {
    const row = { user_id: "old", week_start: 20772, done: 2, due: 3, score: 99,
      badge: "trained", streak_weeks: 2, protein_days: 7,
      prs: JSON.stringify([{ exercise: "Squat", kg: 50, reps: 5 }]), updated_at: 123 };
    expect(toSnapshot(row, true)).toMatchObject({ score: 67, scoreVersion: 1, reachedAt: 123,
      breakdown: { bonus: 0, score: 67 }, prs: [{ exercise: "Squat", kg: 50, reps: 5 }] });
    expect(toSnapshot(row, false).prs).toBeNull();
  });
  it("ignores legacy score and awards no bonuses", () => {
    expect(validateSnapshot(old, 20774).score).toBe(67);
  });
  it("calculates raw inputs regardless of claimed score", () => {
    expect(validateSnapshot({ ...old, target: 3, prCount: 1, reachedAt: 1794758400 }, 20774).score).toBe(78);
  });
  it.each([{ target: 8 }, { prCount: -1 }, { reachedAt: -1 }, { target: 3, prCount: 0 }, { target: null }])("rejects malformed or partial raw inputs %j", extra => {
    expect(() => validateSnapshot({ ...old, ...extra }, 20774)).toThrow("bad_snapshot");
  });
});
