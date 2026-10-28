import { useRef, useState } from 'preact/hooks'
import type { ComponentChildren } from 'preact'
import { haptics } from './haptics'

export type SwipeRowProps = {
  /** Called after the row has been swiped past 40% of its width toward the start edge. */
  onDelete: () => void
  /** The delete action's name, for screen readers. */
  label: string
  children?: ComponentChildren
  /** Corner radius of the row, so the red layer behind matches it. */
  radius?: number
  /** Add a hidden Delete button for rows that have no delete button of their own. */
  srDelete?: boolean
}

const THRESHOLD = 0.4
const rtl = () => document.documentElement.dir === 'rtl'

/**
 * Swipe a row toward its start edge (left in English, right in Arabic) to delete it; the caller shows Undo.
 * Pointer events only (touch and mouse), no library. Vertical scrolling is left to the browser (touch-action: pan-y).
 */
export function SwipeRow({ onDelete, label, children, radius = 16, srDelete }: SwipeRowProps) {
  const box = useRef<HTMLDivElement>(null)
  const s = useRef({ x0: 0, y0: 0, active: false, suppress: false })
  const [pull, setPull] = useState(0) // px moved toward the start edge
  const [settling, setSettling] = useState(false)
  const wasOver = useRef(false)

  const onDown = (e: PointerEvent) => {
    if (e.pointerType === 'mouse' && e.button !== 0) return
    s.current = { x0: e.clientX, y0: e.clientY, active: false, suppress: false }
    setSettling(false)
  }
  const onMove = (e: PointerEvent) => {
    const st = s.current
    if (e.buttons === 0 && e.pointerType === 'mouse') return
    const dx = e.clientX - st.x0
    const dy = e.clientY - st.y0
    if (!st.active) {
      if (Math.abs(dx) < 10 || Math.abs(dx) < Math.abs(dy) * 1.5) return
      st.active = true
      try { (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId) } catch { /* ignore */ }
    }
    const width = box.current?.offsetWidth ?? 300
    const next = Math.min(width, Math.max(0, dx * (rtl() ? 1 : -1)))
    setPull(next)
    // One tick each time the swipe crosses the delete line (the bin pops through its class change).
    const over = next > width * THRESHOLD
    if (over && !wasOver.current) haptics.tick()
    wasOver.current = over
  }
  const onUp = () => {
    const st = s.current
    if (!st.active) return
    st.active = false
    wasOver.current = false
    st.suppress = true
    setTimeout(() => { st.suppress = false }, 60)
    const width = box.current?.offsetWidth ?? 300
    setSettling(true)
    if (pull > width * THRESHOLD) {
      setPull(width)
      setTimeout(onDelete, 160)
      // If the row is still mounted afterwards (Undo brought it back), show it settled, not stuck in the red state.
      setTimeout(() => { setSettling(true); setPull(0) }, 700)
    } else setPull(0)
  }

  const x = pull * (rtl() ? 1 : -1)
  return (
    <div class="kit-swipe" ref={box} style={{ borderRadius: `${radius}px` }}>
      <div class="kit-swipe-bg" aria-hidden="true" style={{ opacity: pull > 0 ? 1 : 0 }}>
        <span class={pull > (box.current?.offsetWidth ?? 300) * THRESHOLD ? 'kit-swipe-bin kit-swipe-bin-over' : 'kit-swipe-bin'}>
          <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" focusable="false"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z" /></svg>
        </span>
      </div>
      <div
        class={`kit-swipe-front${settling ? ' kit-swipe-settle' : ''}`}
        style={{ transform: pull ? `translateX(${x}px)` : undefined }}
        onPointerDown={onDown} onPointerMove={onMove} onPointerUp={onUp} onPointerCancel={onUp}
        onClickCapture={(e) => { if (s.current.suppress) { e.preventDefault(); e.stopPropagation() } }}
      >
        {children}
      </div>
      {srDelete && <button type="button" class="kit-sr" onClick={onDelete}>{label}</button>}
    </div>
  )
}
