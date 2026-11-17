import { describe, expect, it, vi } from 'vitest'
import { holdCueEvent, speakCue } from './tts'

describe('live tts helpers', () => {
  it('maps hold elapsed seconds to cue events', () => {
    expect(holdCueEvent(-3, 20)).toBe('READY_3')
    expect(holdCueEvent(-1, 20)).toBe('READY_1')
    expect(holdCueEvent(0, 20)).toBe('START')
    expect(holdCueEvent(10, 20)).toBe('HALFWAY')
    expect(holdCueEvent(17, 20)).toBe('COUNT_3')
    expect(holdCueEvent(19, 20)).toBe('COUNT_1')
    expect(holdCueEvent(20, 20)).toBe('DONE')
    expect(holdCueEvent(4, 8)).toBeNull()
  })

  it('does not speak or chime when disabled or backgrounded, and falls back when no speech API exists', () => {
    const fallback = vi.fn()
    expect(speakCue('Go', 'en', false, true, fallback)).toBe(false)
    expect(speakCue('Go', 'en', true, false, fallback)).toBe(false)
    expect(fallback).not.toHaveBeenCalled()

    // Node test environment has no window.speechSynthesis -> invokes chime fallback
    expect(speakCue('Go', 'en', true, true, fallback)).toBe(false)
    expect(fallback).toHaveBeenCalledTimes(1)
  })
})
