// Beep, buzz and keep-the-screen-on helpers for the live workout. All feature-detected: any of them may be missing.

let ctx: AudioContext | null = null

/** Call from a tap: iOS only lets an AudioContext start (or resume) inside a user gesture. */
export function unlockAudio(): void {
  try {
    const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    if (!Ctor) return
    ctx ??= new Ctor()
    if (ctx.state === 'suspended') void ctx.resume()
  } catch { /* no audio: the buzz and the screen still work */ }
}

/** A short beep (default 880 Hz, 0.25 s). */
export function beep(freq = 880, seconds = 0.25): void {
  try {
    if (!ctx || ctx.state !== 'running') return
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    const t0 = ctx.currentTime
    osc.frequency.value = freq
    gain.gain.setValueAtTime(0.0001, t0)
    gain.gain.exponentialRampToValueAtTime(0.25, t0 + 0.02)
    gain.gain.exponentialRampToValueAtTime(0.0001, t0 + seconds)
    osc.connect(gain).connect(ctx.destination)
    osc.start(t0)
    osc.stop(t0 + seconds + 0.02)
  } catch { /* ignore */ }
}

export function buzz(pattern: number | number[] = 40): void {
  try { navigator.vibrate?.(pattern) } catch { /* not supported (iOS Safari) */ }
}

/** Something failed: a short double buzz (Android Haptics.error). */
export const buzzError = (): void => buzz([30, 60, 30])

/** Rest or hold finished: beep plus a long buzz. */
export function alarm(vibrate: boolean): void {
  beep(988, 0.35)
  if (vibrate) buzz([200, 100, 200])
}

type Lock = { release: () => Promise<void> }
/** Holds a Screen Wake Lock while open; re-requests it when the tab comes back to the front. Returns the release function. */
export function keepScreenOn(): () => void {
  let lock: Lock | null = null
  let stopped = false
  const request = async () => {
    try {
      const wl = (navigator as unknown as { wakeLock?: { request: (t: 'screen') => Promise<Lock> } }).wakeLock
      if (!wl || stopped || document.visibilityState !== 'visible' || lock) return
      lock = await wl.request('screen')
      if (stopped) { void lock.release().catch(() => {}); lock = null }
    } catch { lock = null /* denied, e.g. low battery */ }
  }
  const onVisible = () => {
    if (document.visibilityState === 'visible') { lock = null; void request() } // the browser dropped it when hidden
  }
  document.addEventListener('visibilitychange', onVisible)
  void request()
  return () => {
    stopped = true
    document.removeEventListener('visibilitychange', onVisible)
    try { void lock?.release().catch(() => {}) } catch { /* ignore */ }
    lock = null
  }
}
