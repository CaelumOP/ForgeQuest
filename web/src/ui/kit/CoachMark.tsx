import type { ComponentChildren } from 'preact'
import { useEffect, useLayoutEffect, useRef, useState } from 'preact/hooks'
import { t, useLang } from '../../i18n'
import { Button } from './basics'
import {
  acquireTip, placeTip, releaseTip, shouldShow, skipTour, stepSeen, waitForTipSlot, type CoachStep,
} from './coach-mark'

export * from './coach-mark'

export type CoachMarkProps = {
  id: CoachStep
  title: string
  body: string
  enabled?: boolean
  below?: boolean
  children: ComponentChildren
}

const SHOW_DELAY_MS = 700

/**
 * Wraps [children] and shows a small persistent tip next to it the first time (Android CoachMarkBox).
 * At most one tip is visible app-wide; "Got it" or a tap outside closes this tip, "Skip tips" ends the tour for good.
 */
export function CoachMark({ id, title, body, enabled = true, below = false, children }: CoachMarkProps) {
  useLang()
  const [open, setOpen] = useState(false)
  const anchorRef = useRef<HTMLSpanElement>(null)
  const tipRef = useRef<HTMLDivElement>(null)
  const tokenRef = useRef<symbol | null>(null)
  if (!tokenRef.current) tokenRef.current = Symbol(id)

  const close = () => {
    setOpen(false)
    releaseTip(tokenRef.current!)
  }

  const onSkip = () => {
    skipTour()
    close()
  }

  useEffect(() => {
    const token = tokenRef.current!
    if (!enabled || !shouldShow(id, enabled)) {
      setOpen(false)
      releaseTip(token)
      return
    }
    let cancelled = false
    let timer: number | undefined
    let stopWait: (() => void) | undefined

    const tryStart = () => {
      if (cancelled || !shouldShow(id, enabled)) {
        stopWait?.()
        stopWait = undefined
        return
      }
      if (!acquireTip(token)) {
        if (!stopWait) stopWait = waitForTipSlot(tryStart)
        return
      }
      stopWait?.()
      stopWait = undefined
      timer = window.setTimeout(() => {
        timer = undefined
        if (cancelled || !shouldShow(id, enabled)) {
          releaseTip(token)
          return
        }
        stepSeen(id)
        setOpen(true)
      }, SHOW_DELAY_MS)
    }

    tryStart()

    return () => {
      cancelled = true
      stopWait?.()
      if (timer !== undefined) clearTimeout(timer)
      setOpen(false)
      releaseTip(token)
    }
  }, [id, enabled])

  const positionTip = () => {
    const wrap = anchorRef.current
    const tipEl = tipRef.current
    if (!wrap || !tipEl) return
    const target = (wrap.firstElementChild as HTMLElement | null) ?? wrap
    if (typeof target.getBoundingClientRect !== 'function' || typeof tipEl.getBoundingClientRect !== 'function') return
    const anchor = target.getBoundingClientRect()
    const tip = tipEl.getBoundingClientRect()
    const topBar = document.querySelector<HTMLElement>('.top-bar, .lw-head')
    const tabBar = document.querySelector<HTMLElement>('.tab-bar')
    const rtl = document.documentElement?.dir === 'rtl'
    const pos = placeTip({
      anchor,
      tip: { width: tip.width || 280, height: tip.height || 120 },
      viewport: { width: window.innerWidth, height: window.innerHeight },
      below,
      rtl,
      topBarBottom: topBar?.getBoundingClientRect?.().bottom ?? 0,
      tabBar: tabBar?.getBoundingClientRect?.() ?? null,
    })
    tipEl.style.insetBlockStart = `${pos.blockStart}px`
    tipEl.style.insetInlineStart = `${pos.inlineStart}px`
    // The caret sits under (or over) the middle of the control, so the tip visibly points at it.
    const width = tip.width || 280
    const centre = anchor.left + anchor.width / 2
    const fromStart = rtl ? window.innerWidth - pos.inlineStart - centre : centre - pos.inlineStart
    tipEl.style.setProperty('--caret', `${Math.min(width - 28, Math.max(28, Math.round(fromStart)))}px`)
    tipEl.classList.toggle('kit-coach-above', pos.blockStart < anchor.top)
  }

  useLayoutEffect(() => {
    if (open) positionTip()
  }, [open, below, title, body])

  useEffect(() => {
    if (!open) return
    positionTip()
    const onOutside = (e: PointerEvent) => {
      const tipEl = tipRef.current
      if (tipEl && e.target instanceof Node && tipEl.contains(e.target)) return
      close()
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close()
    }
    window.addEventListener('resize', positionTip)
    window.addEventListener('scroll', positionTip, true)
    window.visualViewport?.addEventListener('resize', positionTip)
    document.addEventListener('pointerdown', onOutside, true)
    document.addEventListener('keydown', onKey, true)
    return () => {
      window.removeEventListener('resize', positionTip)
      window.removeEventListener('scroll', positionTip, true)
      window.visualViewport?.removeEventListener('resize', positionTip)
      document.removeEventListener('pointerdown', onOutside, true)
      document.removeEventListener('keydown', onKey, true)
    }
  }, [open, below, title, body])

  return (
    <>
      <span ref={anchorRef} class="kit-coach-anchor">{children}</span>
      {open && (
        <div ref={tipRef} class="kit-coach" role="status" aria-live="polite">
          <div class="kit-coach-title">{title}</div>
          <p class="kit-coach-body">{body}</p>
          <div class="kit-coach-actions">
            <Button variant="text" class="kit-coach-skip" onClick={onSkip}>{t('coach_skip')}</Button>
            <Button variant="text" onClick={close}>{t('coach_got_it')}</Button>
          </div>
        </div>
      )}
    </>
  )
}
