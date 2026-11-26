import { useEffect, useRef } from 'preact/hooks'
import type { ChipProps } from './basics'
import { reducedMotion } from './haptics'

const cx = (...a: (string | false | undefined)[]) => a.filter(Boolean).join(' ')

export type SlidingSegmentedOption<T extends string> = {
  value: T
  label: string
  disabled?: boolean
}

export type SlidingSegmentedPillsProps<T extends string> = {
  options: SlidingSegmentedOption<T>[]
  value: T | null
  onChange: (v: T) => void
  label: string
  accent?: ChipProps['accent']
}

/** Normalized horizontal start offset (0..1 of track width) for the sliding pill at `position`. */
export function slidingPillOffsetFraction(position: number, count: number, isRtl: boolean): number {
  if (count <= 0) return 0
  const clamped = Math.min(Math.max(position, 0), count - 1)
  const visual = isRtl ? count - 1 - clamped : clamped
  return visual / count
}

/** TranslateX percentage (of a single pill's width) from the inline-start edge for `index`. */
export function slidingPillTranslatePercent(index: number, count: number, isRtl: boolean): number {
  if (count <= 0) return 0
  const clamped = Math.min(Math.max(index, 0), count - 1)
  return clamped * 100 * (isRtl ? -1 : 1)
}

/**
 * Equal-width segmented pill selector with a single selection indicator that slides smoothly between options.
 * Mirrors in RTL, jumps immediately under reduced motion, enforces >= 48 px targets, and exposes radio-group semantics.
 */
export function SlidingSegmentedPills<T extends string>({
  options,
  value,
  onChange,
  label,
  accent = 'teal',
}: SlidingSegmentedPillsProps<T>) {
  const btnRefs = useRef<(HTMLButtonElement | null)[]>([])
  const selectedIndex = value == null ? -1 : options.findIndex((o) => o.value === value)
  const hadSelectionRef = useRef(selectedIndex >= 0)
  const snap = !hadSelectionRef.current || reducedMotion()

  useEffect(() => {
    hadSelectionRef.current = selectedIndex >= 0
  }, [selectedIndex])

  if (options.length === 0) return null

  const moveFocusAndSelect = (fromIndex: number, step: number) => {
    const n = options.length
    for (let offset = 1; offset <= n; offset++) {
      const idx = (fromIndex + step * offset + n) % n
      const opt = options[idx]
      if (opt && !opt.disabled) {
        btnRefs.current[idx]?.focus?.()
        onChange(opt.value)
        return
      }
    }
  }

  const onKeyDown = (e: KeyboardEvent, i: number) => {
    const el = e.currentTarget as HTMLElement | null
    const isRtl =
      (typeof getComputedStyle === 'function' && el ? getComputedStyle(el).direction === 'rtl' : false) ||
      (typeof document !== 'undefined' && document.documentElement?.getAttribute?.('dir') === 'rtl')
    const nextKey = isRtl ? 'ArrowLeft' : 'ArrowRight'
    const prevKey = isRtl ? 'ArrowRight' : 'ArrowLeft'
    if (e.key === nextKey || e.key === 'ArrowDown') {
      e.preventDefault()
      moveFocusAndSelect(i, 1)
    } else if (e.key === prevKey || e.key === 'ArrowUp') {
      e.preventDefault()
      moveFocusAndSelect(i, -1)
    } else if (e.key === 'Home') {
      e.preventDefault()
      const first = options.findIndex((o) => !o.disabled)
      if (first >= 0) {
        btnRefs.current[first]?.focus?.()
        onChange(options[first]!.value)
      }
    } else if (e.key === 'End') {
      e.preventDefault()
      for (let idx = options.length - 1; idx >= 0; idx--) {
        if (!options[idx]!.disabled) {
          btnRefs.current[idx]?.focus?.()
          onChange(options[idx]!.value)
          break
        }
      }
    }
  }

  return (
    <div class={cx('kit-slide-seg', `kit-accent-${accent}`)} role="radiogroup" aria-label={label}>
      <div class="kit-slide-track" style={{ gridTemplateColumns: `repeat(${options.length}, minmax(0, 1fr))` }}>
        {selectedIndex >= 0 && (
          <div
            class={cx('kit-slide-pill', snap && 'kit-slide-pill-snap')}
            aria-hidden="true"
            style={{
              inlineSize: `calc(100% / ${options.length})`,
              transform: `translateX(calc(${selectedIndex} * 100% * var(--seg-dir, 1)))`,
            }}
          />
        )}
        {options.map((o, i) => {
          const on = i === selectedIndex
          return (
            <button
              key={o.value}
              ref={(el) => { btnRefs.current[i] = el }}
              type="button"
              role="radio"
              aria-checked={on}
              disabled={o.disabled}
              class={cx('kit-slide-opt', on && 'kit-slide-opt-on')}
              onClick={() => onChange(o.value)}
              onKeyDown={(e) => onKeyDown(e, i)}
            >
              <span class="kit-slide-label">{o.label}</span>
            </button>
          )
        })}
      </div>
    </div>
  )
}
