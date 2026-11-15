import { describe, expect, it } from "vitest";
import { combineWeek, sharedStreak, WeekState } from "../src/streak";

// 20401 is a Sunday epoch day (20401 % 7 == 3). Weeks: W0 = current, W1 = last week, ...
const W0 = 20401;
const wk = (n: number) => W0 - 7 * n;
/** 01:00 Riyadh on epoch day d, in unix seconds. */
const sec = (d: number) => d * 86400 - 3 * 3600 + 3600;
const states = (entries: Array<[number, WeekState]>) => new Map(entries);
const longAgo = sec(wk(30));
const WED = W0 + 3;

describe("combineWeek", () => {
  it.each<[WeekState, WeekState, string]>([
    ["hit", "hit", "count"],
    ["hit", "free", "skip"],
    ["free", "hit", "skip"],
    ["free", "free", "skip"],
    ["hit", "miss", "stop"],
    ["miss", "hit", "stop"],
    ["free", "miss", "stop"],
    ["miss", "miss", "stop"],
  ])("%s + %s -> %s", (a, b, verdict) => {
    expect(combineWeek(a, b)).toBe(verdict);
  });
});

describe("sharedStreak", () => {
  it("counts finished weeks both hit, plus the current week when both already hit", () => {
    const both = states([[wk(0), "hit"], [wk(1), "hit"], [wk(2), "hit"]]);
    expect(sharedStreak({ friendsSinceSec: longAgo, today: WED, mine: both, theirs: both })).toBe(3);
  });

  it("an unfinished current week never breaks the streak", () => {
    const mine = states([[wk(0), "open"], [wk(1), "hit"], [wk(2), "hit"]]);
    const theirs = states([[wk(0), "hit"], [wk(1), "hit"], [wk(2), "hit"]]);
    expect(sharedStreak({ friendsSinceSec: longAgo, today: WED, mine, theirs })).toBe(2);
  });

  it("stops at the first finished week one of you missed; missing rows and stale open rows are misses", () => {
    const mine = states([[wk(1), "hit"], [wk(2), "hit"], [wk(3), "hit"], [wk(4), "hit"]]);
    const theirs = states([[wk(1), "hit"], [wk(2), "open"], [wk(4), "hit"]]);
    expect(sharedStreak({ friendsSinceSec: longAgo, today: WED, mine, theirs })).toBe(1);
  });

  it("a free week neither counts nor breaks", () => {
    const mine = states([[wk(1), "hit"], [wk(2), "free"], [wk(3), "hit"]]);
    const theirs = states([[wk(1), "hit"], [wk(2), "hit"], [wk(3), "hit"]]);
    expect(sharedStreak({ friendsSinceSec: longAgo, today: WED, mine, theirs })).toBe(2);
  });

  it("only counts weeks since the friendship began (that week included)", () => {
    const both = states([[wk(1), "hit"], [wk(2), "hit"], [wk(3), "hit"], [wk(4), "hit"]]);
    const since = sec(wk(2) + 4); // became friends on the Thursday of week 2
    expect(sharedStreak({ friendsSinceSec: since, today: WED, mine: both, theirs: both })).toBe(2);
  });

  it("new friends this week: 0 until both hit, then 1", () => {
    const since = sec(W0 + 1);
    const hit = states([[wk(0), "hit"], [wk(1), "hit"]]);
    expect(sharedStreak({ friendsSinceSec: since, today: WED, mine: hit, theirs: states([[wk(0), "open"]]) })).toBe(0);
    expect(sharedStreak({ friendsSinceSec: since, today: WED, mine: hit, theirs: hit })).toBe(1);
  });

  it("Sunday/Monday grace: last week still open does not zero the streak", () => {
    const mine = states([[wk(1), "hit"], [wk(2), "hit"], [wk(3), "hit"]]);
    const theirs = states([[wk(1), "open"], [wk(2), "hit"], [wk(3), "hit"]]);
    expect(sharedStreak({ friendsSinceSec: longAgo, today: W0, mine, theirs })).toBe(2); // Sunday
    expect(sharedStreak({ friendsSinceSec: longAgo, today: W0 + 1, mine, theirs })).toBe(2); // Monday
    expect(sharedStreak({ friendsSinceSec: longAgo, today: W0 + 2, mine, theirs })).toBe(0); // Tuesday: final
  });

  it("no history at all is 0", () => {
    expect(sharedStreak({ friendsSinceSec: longAgo, today: WED, mine: new Map(), theirs: new Map() })).toBe(0);
  });
});
