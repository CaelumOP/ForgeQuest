import type { ComponentChildren } from 'preact'
import { useEffect, useRef, useState } from 'preact/hooks'
import { reducedMotion } from './haptics'

/** False on the first frame, then true: a ring drawn at 0 first sweeps to its value through the CSS transition. */
export function useSwept(): boolean {
  const [swept, setSwept] = useState(() => reducedMotion())
  useEffect(() => {
    if (swept) return
    // Two frames, so the browser has painted the empty ring before the transition starts.
    let id = requestAnimationFrame(() => { id = requestAnimationFrame(() => setSwept(true)) })
    return () => cancelAnimationFrame(id)
  }, [])
  return swept
}

/**
 * A number that glides to a new value over 400 ms (decelerating, like Android's 0.8/400 spring) instead of jumping.
 * The first value shows at once; reduced motion always jumps.
 */
export function useTweened(value: number): number {
  const [shown, setShown] = useState(value)
  const from = useRef(value)
  const live = useRef(value)
  live.current = shown
  useEffect(() => {
    if (reducedMotion() || from.current === value) { from.current = value; setShown(value); return }
    const start = live.current
    from.current = value
    const t0 = performance.now()
    let raf = 0
    const frame = (now: number) => {
      const p = Math.min(1, (now - t0) / 400)
      const eased = 1 - (1 - p) ** 3
      setShown(start + (value - start) * eased)
      if (p < 1) raf = requestAnimationFrame(frame)
    }
    raf = requestAnimationFrame(frame)
    return () => cancelAnimationFrame(raf)
  }, [value])
  return shown
}

export type RingProps = {
  /** 0..1, clamped. */
  value: number
  /** CSS variable name of the arc, e.g. '--teal'. */
  color?: string
  /** CSS variable name of the track, e.g. '--teal-track'. */
  track?: string
  size?: number
  stroke?: number
  /** Accessible name, e.g. "Protein". */
  label: string
  /** What a screen reader says for the value, e.g. "66 of 150 g". Defaults to the percentage. */
  valueText?: string
  /** Centre content (number, icon). */
  children?: ComponentChildren
  /** Position among rings shown together: each sweeps in 60 ms after the one before. */
  index?: number
}
/** Progress ring like Rings.kt: round caps, starts at 12 o'clock and runs clockwise (mirrored in RTL). */
export function Ring({ value, color = '--teal', track = '--teal-track', size = 96, stroke = 10, label, valueText, children, index = 0 }: RingProps) {
  const v = Math.min(1, Math.max(0, Number.isFinite(value) ? value : 0))
  const swept = useSwept()
  const r = (size - stroke) / 2
  const c = 2 * Math.PI * r
  const pct = Math.round(v * 100)
  return (
    // A progress bar to screen readers, read as one thing: its children (the centre number) are presentational.
    <div
      class="kit-ring" style={{ inlineSize: `${size}px`, blockSize: `${size}px` }}
      role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={pct} aria-valuetext={valueText ?? `${pct}%`}
    >
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden="true" focusable="false">
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={`var(${track})`} stroke-width={stroke} />
        {v > 0 && (
          <circle
            class="kit-ring-arc"
            cx={size / 2} cy={size / 2} r={r} fill="none" stroke={`var(${color})`} stroke-width={stroke} stroke-linecap="round"
            stroke-dasharray={`${c * (swept ? v : 0)} ${c}`} transform={`rotate(-90 ${size / 2} ${size / 2})`}
            style={{ transitionDelay: `${index * 60}ms` }}
          />
        )}
      </svg>
      <div class="kit-ring-center">{children}</div>
    </div>
  )
}
