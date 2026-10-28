import { useId, useRef, useState } from 'preact/hooks'
import { t } from '../../i18n'
import { AddIcon, RemoveIcon } from '../icons'
import { haptics } from './haptics'

export type TextFieldProps = {
  label: string
  value: string
  onInput: (v: string) => void
  error?: string
  hint?: string
  type?: 'text' | 'email' | 'search' | 'password' | 'url'
  multiline?: boolean
  placeholder?: string
  autocomplete?: string
  disabled?: boolean
}
/** Label is always visible above the field; the error replaces the hint below it. */
export function TextField({ label, value, onInput, error, hint, type = 'text', multiline, placeholder, autocomplete, disabled }: TextFieldProps) {
  const id = useId()
  const note = error ?? hint
  const common = {
    id, value, placeholder, disabled, autocomplete,
    class: 'kit-input',
    'aria-invalid': error ? ('true' as const) : undefined,
    'aria-describedby': note ? `${id}-n` : undefined,
  }
  return (
    <div class="kit-field">
      <label class="kit-label" for={id}>{label}</label>
      {multiline
        ? <textarea {...common} rows={4} onInput={(e) => onInput((e.target as HTMLTextAreaElement).value)} />
        : <input {...common} type={type as 'text'} onInput={(e) => onInput((e.target as HTMLInputElement).value)} />}
      {note && <span id={`${id}-n`} class={error ? 'kit-error' : 'kit-hint'} role={error ? 'alert' : undefined}>{note}</span>}
    </div>
  )
}

/** Accepts "12,5" as well as "12.5" and Arabic-Indic digits. Returns NaN when empty or invalid. */
export function parseNumber(s: string): number {
  const n = s
    .replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 0x660))
    .replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x6f0))
    .replace('٫', '.').replace(',', '.').trim()
  return n === '' ? NaN : Number(n)
}

export type NumberFieldProps = {
  label: string
  /** null while empty. */
  value: number | null
  onChange: (v: number | null) => void
  error?: string
  hint?: string
  unit?: string
  min?: number
  max?: number
  /** Allow decimals (default false). */
  decimal?: boolean
}
/** Keeps what the user typed while editing and reports a number (or null) on every keystroke. */
export function NumberField({ label, value, onChange, error, hint, unit, min, max, decimal }: NumberFieldProps) {
  const [text, setText] = useState(value == null ? '' : String(value))
  const [last, setLast] = useState(value)
  if (value !== last) { setLast(value); setText(value == null ? '' : String(value)) }
  const id = useId()
  const note = error ?? hint
  const handle = (s: string) => {
    setText(s)
    const n = parseNumber(s)
    const ok = Number.isFinite(n) && (min == null || n >= min) && (max == null || n <= max)
    const next = ok ? n : null
    setLast(next)
    onChange(next)
  }
  return (
    <div class="kit-field">
      <label class="kit-label" for={id}>{unit ? `${label} (${unit})` : label}</label>
      <input
        id={id} class="kit-input kit-num" type="text" inputMode={decimal ? 'decimal' : 'numeric'} value={text}
        aria-invalid={error ? 'true' : undefined} aria-describedby={note ? `${id}-n` : undefined}
        onInput={(e) => handle((e.target as HTMLInputElement).value)}
      />
      {note && <span id={`${id}-n`} class={error ? 'kit-error' : 'kit-hint'} role={error ? 'alert' : undefined}>{note}</span>}
    </div>
  )
}

export type StepperProps = { value: number; onChange: (v: number) => void; min?: number; max?: number; step?: number; label: string; format?: (n: number) => string }
/** Minus / number / plus with 44px targets; `label` names it for screen readers. Tap the number to type one. */
export function Stepper({ value, onChange, min = 0, max = Infinity, step = 1, label, format }: StepperProps) {
  const [draft, setDraft] = useState<string | null>(null)
  // The number rolls up on + and down on − (only after a step, so it doesn't roll when the screen opens).
  const dir = useRef<0 | 1 | -1>(0)
  const set = (n: number) => onChange(Math.min(max, Math.max(min, Math.round(n * 1000) / 1000)))
  const step1 = (d: 1 | -1, from: number) => {
    const next = Math.min(max, Math.max(min, Math.round((from + d * step) * 1000) / 1000))
    if (next === value) return
    dir.current = d
    haptics.lightTick()
    set(next)
  }
  const shown = format ? format(value) : String(value)
  const typed = draft == null ? NaN : parseNumber(draft)
  // While typing, − and + step from the typed number (the blur that saves it hasn't re-rendered yet).
  const base = Number.isFinite(typed) ? typed : value
  const commit = () => {
    if (Number.isFinite(typed)) set(typed)
    setDraft(null)
  }
  return (
    <div class="kit-stepper" role="group" aria-label={label}>
      <button type="button" class="kit-iconbtn kit-iconbtn-tonal" aria-label={t('cd_decrease', shown)} disabled={value <= min} onClick={() => step1(-1, base)}><RemoveIcon /></button>
      {draft == null
        // A slider to screen readers: it names the value, and arrow keys (or a swipe) step it. A tap types one.
        ? <button
            type="button" class="kit-stepper-val" role="slider" aria-label={label}
            aria-valuenow={value} aria-valuemin={Number.isFinite(min) ? min : undefined} aria-valuemax={Number.isFinite(max) ? max : undefined} aria-valuetext={shown}
            onClick={() => setDraft(String(value))}
            onKeyDown={(e) => {
              if (e.key === 'ArrowUp') { e.preventDefault(); step1(1, value) }
              if (e.key === 'ArrowDown') { e.preventDefault(); step1(-1, value) }
            }}
          ><span key={shown} class={dir.current === 0 ? 'kit-roll' : `kit-roll ${dir.current > 0 ? 'kit-roll-up' : 'kit-roll-down'}`}>{shown}</span></button>
        : <input
            class="kit-stepper-input" aria-label={label} value={draft} autoFocus
            inputMode={Number.isInteger(step) && Number.isInteger(min) ? 'numeric' : 'decimal'} enterKeyHint="done"
            onFocus={(e) => (e.target as HTMLInputElement).select()}
            onInput={(e) => setDraft((e.target as HTMLInputElement).value)}
            onBlur={commit}
            onKeyDown={(e) => { if (e.key === 'Enter') (e.target as HTMLInputElement).blur(); if (e.key === 'Escape') setDraft(null) }}
          />}
      <button type="button" class="kit-iconbtn kit-iconbtn-tonal" aria-label={t('cd_increase', shown)} disabled={value >= max} onClick={() => step1(1, base)}><AddIcon /></button>
    </div>
  )
}
