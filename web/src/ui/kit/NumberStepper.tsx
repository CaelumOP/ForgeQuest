import { useRef, useState } from 'preact/hooks'
import { t } from '../../i18n'
import type { PlateBreakdown } from '../../domain/plate-math'
import { AddIcon, RemoveIcon } from '../icons'
import { haptics } from './haptics'
import { NumberEntryKeypad } from './NumberEntryKeypad'

export type NumberStepperProps = {
  value: number
  onChange: (v: number) => void
  min?: number
  max?: number
  step?: number
  decimals?: number
  unit?: string
  label: string
  placeholder?: string | null
  format?: (n: number) => string
  quickDeltas?: readonly number[]
  lastTimeValue?: number | null
  lastTimeLabel?: string | null
  plateCalculator?: ((displayWeight: number) => PlateBreakdown | null) | null
}

/**
 * Live workout numeric stepper: keeps the ± buttons and opens a bottom-docked
 * thumb-zone keypad when the number is tapped.
 */
export function NumberStepper({
  value,
  onChange,
  min = 0,
  max = 999,
  step = 1,
  decimals = 0,
  unit = '',
  label,
  placeholder = null,
  format,
  quickDeltas = [],
  lastTimeValue = null,
  lastTimeLabel = null,
  plateCalculator = null,
}: NumberStepperProps) {
  const [keypadOpen, setKeypadOpen] = useState(false)
  const dir = useRef<0 | 1 | -1>(0)

  const round = (n: number) => {
    const factor = 10 ** decimals
    return Math.min(max, Math.max(min, Math.round(n * factor) / factor))
  }
  const step1 = (d: 1 | -1) => {
    const next = round(value + d * step)
    if (next === value) return
    dir.current = d
    haptics.lightTick()
    onChange(next)
  }

  const shown = format ? format(value) : unit ? `${round(value)} ${unit}` : String(round(value))
  const display = placeholder ?? shown

  return (
    <>
      <div class="kit-stepper" role="group" aria-label={label}>
        <button
          type="button"
          class="kit-iconbtn kit-iconbtn-tonal"
          aria-label={t('cd_decrease', display)}
          disabled={value <= min}
          onClick={() => step1(-1)}
        >
          <RemoveIcon />
        </button>
        <button
          type="button"
          class={`kit-stepper-val${placeholder != null ? ' kit-stepper-placeholder' : ''}`}
          role="slider"
          aria-label={label}
          aria-valuenow={value}
          aria-valuemin={Number.isFinite(min) ? min : undefined}
          aria-valuemax={Number.isFinite(max) ? max : undefined}
          aria-valuetext={display}
          onClick={() => setKeypadOpen(true)}
          onKeyDown={(e) => {
            if (e.key === 'ArrowUp') { e.preventDefault(); step1(1) }
            if (e.key === 'ArrowDown') { e.preventDefault(); step1(-1) }
          }}
        >
          <span
            key={display}
            class={dir.current === 0 ? 'kit-roll' : `kit-roll ${dir.current > 0 ? 'kit-roll-up' : 'kit-roll-down'}`}
          >
            {display}
          </span>
        </button>
        <button
          type="button"
          class="kit-iconbtn kit-iconbtn-tonal"
          aria-label={t('cd_increase', display)}
          disabled={value >= max}
          onClick={() => step1(1)}
        >
          <AddIcon />
        </button>
      </div>
      <NumberEntryKeypad
        open={keypadOpen}
        title={label}
        initial={value}
        unit={unit}
        decimals={decimals}
        min={min}
        max={max}
        quickDeltas={quickDeltas}
        lastTimeValue={lastTimeValue}
        lastTimeLabel={lastTimeLabel}
        plateCalculator={plateCalculator}
        onClose={() => setKeypadOpen(false)}
        onConfirm={(next) => {
          setKeypadOpen(false)
          dir.current = next > value ? 1 : next < value ? -1 : 0
          onChange(round(next))
        }}
      />
    </>
  )
}
