import { describe, expect, it } from 'vitest'
import { restWaveTaper, restWaveTrackStart } from './restWave'

describe('rest wave geometry', () => {
  it('leaves a six pixel visible gap between round caps', () => {
    const start = restWaveTrackStart(316, 0.5)!
    expect((start - 2) - (158 + 2)).toBe(6)
    expect(restWaveTrackStart(316, 0)).toBe(0)
    expect(restWaveTrackStart(316, -1)).toBe(0)
    for (const progress of [0.99, 1, 2]) expect(restWaveTrackStart(316, progress)).toBeNull()
    expect(restWaveTrackStart(20, 0.5)).toBeNull()
    expect(restWaveTrackStart(0, 0.5)).toBeNull()
  })

  it('tapers smoothly and symmetrically, including short waves', () => {
    expect(restWaveTaper(0, 160)).toBe(0)
    expect(restWaveTaper(160, 160)).toBe(0)
    expect(restWaveTaper(16, 160)).toBe(0.5)
    expect(restWaveTaper(144, 160)).toBe(0.5)
    expect(restWaveTaper(80, 160)).toBe(1)
    expect(restWaveTaper(8, 16)).toBe(0.15625)
    expect(restWaveTaper(0, 0)).toBe(0)
    expect(restWaveTaper(-1, 160)).toBe(0)
    expect(restWaveTaper(161, 160)).toBe(0)
    expect(restWaveTaper(0.01, 160) / 0.01).toBeLessThan(0.0001)
  })
})
