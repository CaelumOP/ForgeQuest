import { useEffect, useState } from 'preact/hooks'
import { getLang, t } from '../../i18n'
import { formatPlateNumber, formatPlatesSummary, type PlateBreakdown } from '../../domain/plate-math'
import { unitLabel } from '../../domain/units'
import { ExpandMoreIcon } from '../icons'
import { Button } from './basics'
import { haptics } from './haptics'
import { Sheet } from './Overlays'
import './NumberEntryKeypad.css'

export type KeypadDraft = {
  text: string
  fresh: boolean
}

export function formatCompactNumber(value: number, decimals: number): string {
  if (decimals <= 0) return String(Math.round(value))
  const fixed = value.toFixed(decimals)
  return fixed.includes('.') ? fixed.replace(/\.?0+$/, '') : fixed
}

export function parseKeypadText(text: string): number | null {
  const cleaned = text.trim().replace(',', '.')
  if (cleaned === '' || cleaned === '.') return null
  const n = Number(cleaned)
  return Number.isFinite(n) ? n : null
}

export function createKeypadDraft(initial: number, decimals: number): KeypadDraft {
  return { text: formatCompactNumber(initial, decimals), fresh: true }
}

export function keypadPressDigit(draft: KeypadDraft, digit: string, decimals: number): KeypadDraft {
  if (!/^[0-9]$/.test(digit)) return draft
  const base = draft.fresh ? '' : draft.text
  if (base.length >= 8) return draft
  if (decimals > 0) {
    const dot = base.indexOf('.')
    if (dot >= 0 && base.length - dot - 1 >= decimals) return draft
  }
  const next = base === '0' && digit === '0' ? '0' : base === '0' ? digit : base + digit
  return { text: next, fresh: false }
}

export function keypadPressDecimal(draft: KeypadDraft, decimals: number): KeypadDraft {
  if (decimals <= 0) return draft
  const base = draft.fresh ? '' : draft.text
  if (base.includes('.')) return draft
  return { text: base === '' ? '0.' : `${base}.`, fresh: false }
}

export function keypadBackspace(draft: KeypadDraft): KeypadDraft {
  if (draft.fresh) return { text: '', fresh: false }
  if (!draft.text.length) return draft
  return { text: draft.text.slice(0, -1), fresh: false }
}

export function keypadApplyDelta(
  draft: KeypadDraft,
  delta: number,
  fallback: number,
  min: number,
  max: number,
  decimals: number,
): KeypadDraft {
  const base = parseKeypadText(draft.text) ?? fallback
  const clamped = Math.min(max, Math.max(min, base + delta))
  return { text: formatCompactNumber(clamped, decimals), fresh: false }
}

export function keypadSetExact(value: number, min: number, max: number, decimals: number): KeypadDraft {
  const clamped = Math.min(max, Math.max(min, value))
  return { text: formatCompactNumber(clamped, decimals), fresh: false }
}

/** Resolves barbell plate breakdown for the current keypad readout (or initial value when cleared). */
export function resolveKeypadPlateBreakdown(
  parsed: number | null,
  initial: number,
  plateCalculator?: ((displayWeight: number) => PlateBreakdown | null) | null,
): PlateBreakdown | null {
  if (!plateCalculator) return null
  const target = parsed ?? initial
  if (target <= 0) return null
  const res = plateCalculator(target)
  return res && !res.belowBar ? res : null
}

export type NumberEntryKeypadProps = {
  open: boolean
  title: string
  initial: number
  unit?: string
  decimals?: number
  min?: number
  max?: number
  quickDeltas?: readonly number[]
  lastTimeValue?: number | null
  lastTimeLabel?: string | null
  plateCalculator?: ((displayWeight: number) => PlateBreakdown | null) | null
  onClose: () => void
  onConfirm: (value: number) => void
}

export function NumberEntryKeypad({
  open,
  title,
  initial,
  unit = '',
  decimals = 0,
  min = 0,
  max = 999,
  quickDeltas = [],
  lastTimeValue = null,
  lastTimeLabel = null,
  plateCalculator = null,
  onClose,
  onConfirm,
}: NumberEntryKeypadProps) {
  const [draft, setDraft] = useState<KeypadDraft>(() => createKeypadDraft(initial, decimals))
  const [platesExpanded, setPlatesExpanded] = useState(false)
  useEffect(() => {
    if (open) {
      setDraft(createKeypadDraft(initial, decimals))
      setPlatesExpanded(false)
    }
  }, [open, initial, decimals])

  const parsed = parseKeypadText(draft.text)
  const valid = parsed != null && parsed >= min && parsed <= max
  const showErr = draft.text !== '' && !valid
  const breakdown = resolveKeypadPlateBreakdown(parsed, initial, plateCalculator)
  const plateUnit = breakdown ? unitLabel(breakdown.unit, getLang() === 'ar') : ''
  const rangeText = t(
    'stepper_range',
    formatCompactNumber(min, decimals),
    formatCompactNumber(max, decimals) + (unit ? ` ${unit}` : ''),
  )

  return (
    <Sheet open={open} onClose={onClose} title={title}>
      <div class="nk-wrap">
        <div class="nk-readout">
          <span class={`nk-value${showErr ? ' nk-value-err' : ''}`}>
            {draft.text === '' ? '—' : unit ? `${draft.text} ${unit}` : draft.text}
          </span>
          <span class={`nk-range${showErr ? ' nk-range-err' : ''}`}>{rangeText}</span>
        </div>

        {breakdown && (
          <div class="nk-plates">
            <button
              type="button"
              class="nk-plates-toggle"
              aria-expanded={platesExpanded}
              aria-label={t('live41_plates_breakdown_cd', `${formatPlateNumber(breakdown.targetWeight)} ${plateUnit}`)}
              onClick={() => setPlatesExpanded((v) => !v)}
            >
              <span class="nk-plates-title">{t('live41_plates_title')}</span>
              <span class="nk-plates-summary">
                {breakdown.barOnly
                  ? t('live41_plates_bar_only', `${formatPlateNumber(breakdown.barWeight)} ${plateUnit}`)
                  : t('live41_plates_side', formatPlatesSummary(breakdown.perSide))}
              </span>
              <span class={`nk-plates-chev${platesExpanded ? ' nk-plates-chev-open' : ''}`} aria-hidden="true">
                <ExpandMoreIcon size={18} />
              </span>
            </button>
            {platesExpanded && (
              <div class="nk-plates-body">
                <div class="nk-plates-meta">
                  {[
                    t('live41_plates_bar', `${formatPlateNumber(breakdown.barWeight)} ${plateUnit}`),
                    t('live41_plates_loaded', `${formatPlateNumber(breakdown.loadedWeight)} ${plateUnit}`),
                  ].join(' · ')}
                </div>
                {breakdown.perSide.length === 0 ? (
                  <div class="nk-plates-none">{t('live41_plates_none_needed')}</div>
                ) : (
                  breakdown.perSide.map((p) => (
                    <div key={p.weight} class="nk-plate-row">
                      <span class="nk-plate-w">{`${formatPlateNumber(p.weight)} ${plateUnit}`}</span>
                      <span class="nk-plate-c">{`× ${p.count}`}</span>
                    </div>
                  ))
                )}
                {breakdown.remainder >= 0.05 && (
                  <div class="nk-plate-rem">
                    {t('live41_plates_remainder', `${formatPlateNumber(breakdown.remainder)} ${plateUnit}`)}
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {(quickDeltas.length > 0 || lastTimeValue != null || draft.text !== '') && (
          <div class="nk-chips">
            {quickDeltas.map((delta) => (
              <button
                key={delta}
                type="button"
                class="nk-chip"
                onClick={() => {
                  haptics.lightTick()
                  setDraft((d) => keypadApplyDelta(d, delta, initial, min, max, decimals))
                }}
              >
                {`+${formatCompactNumber(delta, delta % 1 !== 0 ? 1 : 0)}`}
              </button>
            ))}
            {lastTimeValue != null && (
              <button
                type="button"
                class="nk-chip nk-chip-last"
                onClick={() => {
                  haptics.tick()
                  setDraft(keypadSetExact(lastTimeValue, min, max, decimals))
                }}
              >
                {t('live41_keypad_last_time', lastTimeLabel ?? formatCompactNumber(lastTimeValue, decimals))}
              </button>
            )}
            {draft.text !== '' && (
              <button
                type="button"
                class="nk-chip"
                onClick={() => {
                  haptics.lightTick()
                  setDraft({ text: '', fresh: false })
                }}
              >
                {t('live41_keypad_clear')}
              </button>
            )}
          </div>
        )}

        <div class="nk-grid">
          {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((digit) => (
            <button
              key={digit}
              type="button"
              class="nk-key"
              onClick={() => {
                haptics.lightTick()
                setDraft((d) => keypadPressDigit(d, digit, decimals))
              }}
            >
              {digit}
            </button>
          ))}
          {decimals > 0 ? (
            <button
              type="button"
              class="nk-key"
              aria-label={t('live41_keypad_decimal')}
              onClick={() => {
                haptics.lightTick()
                setDraft((d) => keypadPressDecimal(d, decimals))
              }}
            >
              .
            </button>
          ) : (
            <span aria-hidden="true" />
          )}
          <button
            type="button"
            class="nk-key"
            onClick={() => {
              haptics.lightTick()
              setDraft((d) => keypadPressDigit(d, '0', decimals))
            }}
          >
            0
          </button>
          <button
            type="button"
            class="nk-key"
            aria-label={t('live41_keypad_backspace')}
            onClick={() => {
              haptics.lightTick()
              setDraft((d) => keypadBackspace(d))
            }}
          >
            ⌫
          </button>
        </div>

        <Button
          block
          disabled={!valid}
          onClick={() => {
            if (valid && parsed != null) {
              haptics.tick()
              onConfirm(parsed)
            }
          }}
        >
          {t('live41_keypad_done')}
        </Button>
      </div>
    </Sheet>
  )
}
