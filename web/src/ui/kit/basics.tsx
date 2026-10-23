import type { ComponentChildren } from 'preact'
import { ForwardChevron } from '../icons'

const cx = (...a: (string | false | undefined)[]) => a.filter(Boolean).join(' ')

export type CardProps = { children?: ComponentChildren; onClick?: () => void; href?: string; class?: string; padded?: boolean }
/** Borderless rounded card (Android FitCard). Becomes a link or button when href / onClick is given. */
export function Card({ children, onClick, href, class: cls, padded = true }: CardProps) {
  const c = cx('kit-card', padded && 'kit-card-pad', (onClick || href) && 'kit-card-tap', cls)
  if (href) return <a class={c} href={href}>{children}</a>
  if (onClick) return <button type="button" class={c} onClick={onClick}>{children}</button>
  return <div class={c}>{children}</div>
}

export type SectionHeaderProps = { title: string; trailing?: ComponentChildren }
export function SectionHeader({ title, trailing }: SectionHeaderProps) {
  return (
    <div class="kit-section">
      <h2 class="kit-section-title">{title}</h2>
      {trailing}
    </div>
  )
}

export type ButtonProps = {
  children?: ComponentChildren
  onClick?: () => void
  variant?: 'filled' | 'tonal' | 'text'
  loading?: boolean
  disabled?: boolean
  icon?: ComponentChildren
  type?: 'button' | 'submit'
  block?: boolean
  class?: string
}
export function Button({ children, onClick, variant = 'filled', loading, disabled, icon, type = 'button', block, class: cls }: ButtonProps) {
  return (
    <button
      type={type}
      class={cx('kit-btn', `kit-btn-${variant}`, block && 'kit-btn-block', cls)}
      disabled={disabled || loading}
      aria-busy={loading ? 'true' : undefined}
      onClick={onClick}
    >
      {loading ? <span class="kit-spinner" aria-hidden="true" /> : icon}
      <span class="kit-btn-label">{children}</span>
    </button>
  )
}

export type IconButtonProps = { label: string; children: ComponentChildren; onClick?: () => void; disabled?: boolean; tone?: 'plain' | 'tonal' }
export function IconButton({ label, children, onClick, disabled, tone = 'plain' }: IconButtonProps) {
  return (
    <button type="button" class={cx('kit-iconbtn', tone === 'tonal' && 'kit-iconbtn-tonal')} aria-label={label} disabled={disabled} onClick={onClick}>
      {children}
    </button>
  )
}

export type ChipProps = { label: string; selected?: boolean; onClick?: () => void; role?: 'radio' | 'checkbox' | 'button'; accent?: 'teal' | 'blue' | 'gold' | 'coral' | 'purple' }
export function Chip({ label, selected = false, onClick, role = 'button', accent = 'teal' }: ChipProps) {
  const aria = role === 'button' ? { 'aria-pressed': selected } : { role, 'aria-checked': selected }
  return (
    <button type="button" class={cx('kit-chip', `kit-accent-${accent}`, selected && 'kit-chip-on')} onClick={onClick} {...aria}>
      {label}
    </button>
  )
}

export type ChipOption<T extends string> = { value: T; label: string }
export type ChipGroupProps<T extends string> =
  | { options: ChipOption<T>[]; multiple?: false; value: T | null; onChange: (v: T) => void; label: string; accent?: ChipProps['accent'] }
  | { options: ChipOption<T>[]; multiple: true; value: T[]; onChange: (v: T[]) => void; label: string; accent?: ChipProps['accent'] }
/** Single select (radio) or multi select (checkbox) row of chips; wraps on narrow screens. */
export function ChipGroup<T extends string>(p: ChipGroupProps<T>) {
  const role = p.multiple ? 'checkbox' : 'radio'
  return (
    <div class="kit-chips" role={p.multiple ? 'group' : 'radiogroup'} aria-label={p.label}>
      {p.options.map((o) => {
        const on = p.multiple ? p.value.includes(o.value) : p.value === o.value
        const click = () => {
          if (p.multiple) p.onChange(on ? p.value.filter((x) => x !== o.value) : [...p.value, o.value])
          else p.onChange(o.value)
        }
        return <Chip key={o.value} label={o.label} selected={on} role={role} accent={p.accent} onClick={click} />
      })}
    </div>
  )
}

export type ListRowProps = {
  title: string
  subtitle?: string
  icon?: ComponentChildren
  trailing?: ComponentChildren
  href?: string
  onClick?: () => void
  /** Show the end chevron on tappable rows (default true). */
  chevron?: boolean
}
export function ListRow({ title, subtitle, icon, trailing, href, onClick, chevron = true }: ListRowProps) {
  const body = (
    <>
      {icon && <span class="kit-row-icon" aria-hidden="true">{icon}</span>}
      <span class="kit-row-text">
        <span class="kit-row-title">{title}</span>
        {subtitle && <span class="kit-row-sub">{subtitle}</span>}
      </span>
      {trailing && <span class="kit-row-trail">{trailing}</span>}
      {(href || onClick) && chevron && <span class="kit-row-chev" aria-hidden="true"><ForwardChevron size={20} /></span>}
    </>
  )
  if (href) return <a class="kit-row kit-row-tap" href={href}>{body}</a>
  if (onClick) return <button type="button" class="kit-row kit-row-tap" onClick={onClick}>{body}</button>
  return <div class="kit-row">{body}</div>
}

export type StatProps = { value: string | number; label: string; accent?: ChipProps['accent'] | 'text' }
export function Stat({ value, label, accent = 'text' }: StatProps) {
  return (
    <div class="kit-stat">
      <span class={cx('kit-stat-value', `kit-accent-${accent}`)}>{value}</span>
      <span class="kit-stat-label">{label}</span>
    </div>
  )
}

export type EmptyStateProps = { title: string; body?: string; icon?: ComponentChildren; action?: ComponentChildren }
export function EmptyState({ title, body, icon, action }: EmptyStateProps) {
  return (
    <div class="kit-empty">
      {icon && <span class="kit-empty-icon" aria-hidden="true">{icon}</span>}
      <h2 class="kit-empty-title">{title}</h2>
      {body && <p class="kit-empty-body">{body}</p>}
      {action}
    </div>
  )
}
