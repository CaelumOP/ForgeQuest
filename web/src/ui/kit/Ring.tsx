import type { ComponentChildren } from 'preact'

export type RingProps = {
  /** 0..1, clamped. */
  value: number
  /** CSS variable name of the arc, e.g. '--teal'. */
  color?: string
  /** CSS variable name of the track, e.g. '--teal-track'. */
  track?: string
  size?: number
  stroke?: number
  /** Accessible description, e.g. "60 percent of protein". */
  label: string
  /** Centre content (number, icon). */
  children?: ComponentChildren
}
/** Progress ring like Rings.kt: round caps, starts at 12 o'clock and runs clockwise (mirrored in RTL). */
export function Ring({ value, color = '--teal', track = '--teal-track', size = 96, stroke = 10, label, children }: RingProps) {
  const v = Math.min(1, Math.max(0, Number.isFinite(value) ? value : 0))
  const r = (size - stroke) / 2
  const c = 2 * Math.PI * r
  return (
    <div class="kit-ring" style={{ inlineSize: `${size}px`, blockSize: `${size}px` }} role="img" aria-label={`${label} ${Math.round(v * 100)}%`}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden="true" focusable="false">
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={`var(${track})`} stroke-width={stroke} />
        {v > 0 && (
          <circle
            class="kit-ring-arc"
            cx={size / 2} cy={size / 2} r={r} fill="none" stroke={`var(${color})`} stroke-width={stroke} stroke-linecap="round"
            stroke-dasharray={`${c * v} ${c}`} transform={`rotate(-90 ${size / 2} ${size / 2})`}
          />
        )}
      </svg>
      <div class="kit-ring-center">{children}</div>
    </div>
  )
}
