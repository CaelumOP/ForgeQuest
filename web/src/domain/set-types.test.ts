import { describe, expect, it } from 'vitest';
import { SET_TYPES, countsAsWorkingSet, countsForProgression, countsForScore, countsForPR } from './set-types';

describe('set eligibility contract', () => {
  for (const helper of [countsForProgression, countsForScore, countsForPR]) {
    it(`${helper.name} accepts working/failure and preserves legacy blocks`, () => {
      for (const setType of SET_TYPES) expect(helper({ setType, block: 'Main' })).toBe(['WORKING', 'FAILURE'].includes(setType));
      for (const block of ['Warm-up', 'WARM-UP', 'Cool-down']) {
        expect(helper({ block })).toBe(false);
        expect(helper({ block, setType: 'WORKING' })).toBe(false);
      }
      expect(helper({ block: 'Main' })).toBe(true);
      expect(helper({ setType: 'UNKNOWN' })).toBe(false);
    });
  }

  it('excludes autoTicked sets from PRs while keeping them for score and progression', () => {
    const autoSet = { block: 'Main', setType: 'WORKING', autoTicked: true };
    expect(countsForPR(autoSet)).toBe(false);
    expect(countsForScore(autoSet)).toBe(true);
    expect(countsForProgression(autoSet)).toBe(true);
  });

  it('countsAsWorkingSet keeps drop/rest-pause/failure sets but drops warm-ups and extra blocks', () => {
    for (const setType of SET_TYPES) expect(countsAsWorkingSet({ setType, block: 'Main' })).toBe(setType !== 'WARMUP');
    expect(countsAsWorkingSet({ block: 'Main' })).toBe(true);
    expect(countsAsWorkingSet({ block: 'Main', autoTicked: true })).toBe(true);
    for (const block of ['Warm-up', 'Cool-down']) expect(countsAsWorkingSet({ block, setType: 'DROPSET' })).toBe(false);
  });
});
