import type { ComponentChildren } from 'preact'
import { useEffect, useId, useRef, useState } from 'preact/hooks'
import { t } from '../../i18n'
import { CloseIcon } from '../icons'
import { Button } from './basics'

const FOCUSABLE = 'a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])'

/** Esc closes, Tab stays inside the panel, focus returns to the opener on close. */
function useTrap(open: boolean, onClose: () => void) {
  const ref = useRef<HTMLDivElement>(null)
  const closeRef = useRef(onClose)
  closeRef.current = onClose
  useEffect(() => {
    if (!open) return
    const opener = document.activeElement as HTMLElement | null
    const node = ref.current
    const first = node?.querySelector<HTMLElement>(FOCUSABLE)
    ;(first ?? node)?.focus()
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { e.stopPropagation(); closeRef.current(); return }
      if (e.key !== 'Tab' || !node) return
      const items = Array.from(node.querySelectorAll<HTMLElement>(FOCUSABLE))
      if (!items.length) { e.preventDefault(); return }
      const a = items[0], z = items[items.length - 1]
      if (e.shiftKey && document.activeElement === a) { e.preventDefault(); z.focus() }
      else if (!e.shiftKey && document.activeElement === z) { e.preventDefault(); a.focus() }
    }
    document.addEventListener('keydown', onKey, true)
    return () => { document.removeEventListener('keydown', onKey, true); opener?.focus?.() }
  }, [open])
  return ref
}

export type SheetProps = { open: boolean; onClose: () => void; title: string; children?: ComponentChildren }
/** Bottom sheet with backdrop. */
export function Sheet({ open, onClose, title, children }: SheetProps) {
  const ref = useTrap(open, onClose)
  const id = useId()
  if (!open) return null
  return (
    <div class="kit-layer">
      <div class="kit-backdrop" onClick={onClose} />
      <div class="kit-sheet" ref={ref} role="dialog" aria-modal="true" aria-labelledby={id} tabIndex={-1}>
        <div class="kit-sheet-head">
          <h2 id={id} class="kit-sheet-title">{title}</h2>
          <button type="button" class="kit-iconbtn" aria-label={t('cd_close')} onClick={onClose}><CloseIcon /></button>
        </div>
        <div class="kit-sheet-body">{children}</div>
      </div>
    </div>
  )
}

export type DialogProps = {
  open: boolean
  title: string
  message?: string
  confirmLabel?: string
  cancelLabel?: string
  destructive?: boolean
  onConfirm: () => void
  onCancel: () => void
}
/** Confirm dialog (Android AlertDialog). */
export function Dialog({ open, title, message, confirmLabel, cancelLabel, destructive, onConfirm, onCancel }: DialogProps) {
  const ref = useTrap(open, onCancel)
  const id = useId()
  if (!open) return null
  return (
    <div class="kit-layer kit-layer-center">
      <div class="kit-backdrop" onClick={onCancel} />
      <div class="kit-dialog" ref={ref} role="alertdialog" aria-modal="true" aria-labelledby={id} tabIndex={-1}>
        <h2 id={id} class="kit-sheet-title">{title}</h2>
        {message && <p class="kit-dialog-msg">{message}</p>}
        <div class="kit-dialog-actions">
          <Button variant="text" onClick={onCancel}>{cancelLabel ?? t('action_cancel')}</Button>
          <Button variant="text" class={destructive ? 'kit-danger' : undefined} onClick={onConfirm}>{confirmLabel ?? t('action_ok')}</Button>
        </div>
      </div>
    </div>
  )
}

type ToastItem = { id: number; msg: string }
let items: ToastItem[] = []
let seq = 0
const subs = new Set<(i: ToastItem[]) => void>()
const emit = () => subs.forEach((f) => f(items))

/** Show a short message at the bottom; auto-dismisses after 4 s. Mount <ToastHost /> once. */
export function toast(msg: string) {
  const id = ++seq
  items = [...items, { id, msg }].slice(-3)
  emit()
  setTimeout(() => { items = items.filter((x) => x.id !== id); emit() }, 4000)
}

export function ToastHost() {
  const [list, setList] = useState(items)
  useEffect(() => { subs.add(setList); setList(items); return () => { subs.delete(setList) } }, [])
  return (
    <div class="kit-toasts" role="status" aria-live="polite">
      {list.map((i) => <div key={i.id} class="kit-toast">{i.msg}</div>)}
    </div>
  )
}
