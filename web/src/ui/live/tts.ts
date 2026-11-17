import type { Lang } from '../../i18n'

export type HoldCueEvent =
  | 'READY_3'
  | 'READY_2'
  | 'READY_1'
  | 'START'
  | 'HALFWAY'
  | 'COUNT_3'
  | 'COUNT_2'
  | 'COUNT_1'
  | 'DONE'

/** Pure mapping from hold elapsed seconds to a spoken/haptic cue event. */
export function holdCueEvent(elapsedSeconds: number, targetSeconds: number): HoldCueEvent | null {
  const left = targetSeconds - elapsedSeconds
  if (elapsedSeconds === -3) return 'READY_3'
  if (elapsedSeconds === -2) return 'READY_2'
  if (elapsedSeconds === -1) return 'READY_1'
  if (elapsedSeconds === 0) return 'START'
  if (elapsedSeconds === targetSeconds) return 'DONE'
  if (left === 3 && targetSeconds > 3) return 'COUNT_3'
  if (left === 2 && targetSeconds > 2) return 'COUNT_2'
  if (left === 1 && targetSeconds > 1) return 'COUNT_1'
  if (targetSeconds >= 10 && elapsedSeconds === Math.floor(targetSeconds / 2)) return 'HALFWAY'
  return null
}

export function hasSpeechVoice(lang: Lang): boolean {
  if (typeof window === 'undefined' || !('speechSynthesis' in window) || typeof SpeechSynthesisUtterance === 'undefined') {
    return false
  }
  try {
    const voices = window.speechSynthesis.getVoices()
    if (!voices || voices.length === 0) return true
    return voices.some((v) => v.lang.toLowerCase().startsWith(lang))
  } catch {
    return false
  }
}

/**
 * Speaks a foreground-only cue via Web Speech API when `enabled && foreground`.
 * Invokes `onFallback` (chime) when no matching voice is available or speech fails.
 */
export function speakCue(
  text: string,
  lang: Lang,
  enabled: boolean,
  foreground: boolean,
  onFallback: () => void,
): boolean {
  if (!enabled || !foreground || !text.trim()) return false
  if (!hasSpeechVoice(lang)) {
    onFallback()
    return false
  }
  try {
    const synth = window.speechSynthesis
    synth.cancel()
    const utt = new SpeechSynthesisUtterance(text)
    utt.lang = lang === 'ar' ? 'ar-SA' : 'en-US'
    const voices = synth.getVoices()
    const match = voices.find((v) => v.lang.toLowerCase().startsWith(lang))
    if (match) utt.voice = match
    utt.onerror = () => onFallback()
    synth.speak(utt)
    return true
  } catch {
    onFallback()
    return false
  }
}

export function stopSpeech(): void {
  try {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      window.speechSynthesis.cancel()
    }
  } catch { /* ignore */ }
}
