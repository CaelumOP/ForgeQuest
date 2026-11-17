import { describe, expect, it } from 'vitest';
import { SET_TYPES, countsForProgression, countsForScore, countsForPR } from './set-types';

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
});
