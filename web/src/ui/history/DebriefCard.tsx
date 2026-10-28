import { useEffect, useMemo, useState } from 'preact/hooks'
import { getLang, t } from '../../i18n'
import { canDebrief, askCoachAboutWorkout } from '../../data/coach-chat'
import { debriefLines } from '../../data/debrief-text'
import { useSettings, weightUnit } from '../../data/settings'
import { useLibrary, useWorkoutDebrief } from '../../data/workouts'
import { formatWeight } from '../../domain/units'
import type { WorkoutScoreResult } from '../../domain/workout-score'
import { Button, haptics, reducedMotion } from '../kit'
import { scoreNote, scoreReasons, scoreTone } from './helpers'
import './history.css'

/**
 * The workout debrief, calculated from the person's numbers: the score with its reasons, then the headline, what went
 * well (teal), what to watch (gold) and next time (blue). The coach button opens the Coach with the workout's message.
 * `onAsked` runs after the message is left for the Coach (a sheet closes itself).
 */
/** M3 emphasized-decelerate, cubic-bezier(0.05, 0.7, 0.1, 1), solved for x by bisection. */
function emphasizedDecelerate(x: number): number {
  const bez = (a: number, b: number, t: number) => 3 * a * t * (1 - t) ** 2 + 3 * b * t * t * (1 - t) + t ** 3
  let lo = 0, hi = 1, t = x
  for (let i = 0; i < 20; i++) { t = (lo + hi) / 2; if (bez(0.05, 0.1, t) < x) lo = t; else hi = t }
  return bez(0.7, 1, t)
}

/**
 * On the finish screen only (`celebrate`): the score counts up from 0 over 650 ms with an arc sweeping to score/10 and a
 * heavy vibrate as it lands; then the lines rise in 70 ms apart. Reduced motion: the score lands at once, lines fade.
 */
function useCountUp(target: number | null, celebrate: boolean): { shown: number | null; landed: boolean } {
  const animate = celebrate && target != null
  const [shown, setShown] = useState<number | null>(animate && !reducedMotion() ? 0 : target)
  const [landed, setLanded] = useState(!celebrate)
  useEffect(() => {
    if (!celebrate) return
    if (target == null) { setLanded(true); return }
    if (reducedMotion()) { setShown(target); haptics.heavyClick(); setLanded(true); return }
    let raf = 0
    const t0 = performance.now()
    const frame = (now: number) => {
      const p = Math.min(1, (now - t0) / 650)
      setShown(target * emphasizedDecelerate(p))
      if (p < 1) raf = requestAnimationFrame(frame)
      else { haptics.heavyClick(); setLanded(true) }
    }
    raf = requestAnimationFrame(frame)
    return () => cancelAnimationFrame(raf)
  }, [])
  return { shown, landed }
}

export function DebriefCard({ sessionId, score, onAsked, celebrate = false }: { sessionId: number; score?: WorkoutScoreResult | null; onAsked?: () => void; celebrate?: boolean }) {
  const settings = useSettings()
  const library = useLibrary()
  const debrief = useWorkoutDebrief(sessionId)
  const lang = getLang()
  const unit = weightUnit(settings)
  const lines = useMemo(() => {
    const names = new Map(library.map((e) => [e.id, e.name]))
    return debrief ? debriefLines(debrief, lang, (id) => names.get(id) ?? id, (kg) => formatWeight(unit, kg, lang === 'ar')) : null
  }, [debrief, library, lang, unit])
  // The first-ever and comeback headlines already say what the score's note says.
  const sameAsHeadline = debrief?.headline.type === 'FIRST_EVER' || debrief?.headline.type === 'AFTER_BREAK'
  const { shown, landed } = useCountUp(score?.showNumber ? score.score : null, celebrate)
  // Each line waits for the count, then rises in 70 ms after the one above it.
  let line = 0
  const rise = () => {
    if (!celebrate) return {}
    const i = line++
    return landed ? { class: 'hs-deb-rise', style: { animationDelay: `${i * 70}ms` } } : { class: 'hs-deb-wait' }
  }
  const cls = (base: string, r: { class?: string }) => (r.class ? `${base} ${r.class}` : base)
  const ask = () => { void askCoachAboutWorkout(sessionId).then((ok) => { if (ok) { onAsked?.(); location.hash = '#/coach' } }) }
  return (
    <div class="hs-deb-card">
      <div class="hs-deb-head">
        <span class="hs-deb-title">{t('debrief_title')}</span>
        {score?.showNumber && (
          <span class={`hs-deb-gold hs-deb-scorering ${scoreTone(score.score)}`} aria-label={t('debrief_score', score.score)}>
            {/* A thin arc that sweeps to score/10 with the count. */}
            <svg width="52" height="52" viewBox="0 0 52 52" aria-hidden="true" focusable="false">
              <circle cx="26" cy="26" r="24.5" fill="none" stroke="var(--card-high)" stroke-width="3" />
              <circle cx="26" cy="26" r="24.5" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" pathLength={1}
                stroke-dasharray={`${Math.min(1, (shown ?? score.score) / 10)} 1`} transform="rotate(-90 26 26)" />
            </svg>
            <span aria-hidden="true">{t('debrief_score', Math.round(shown ?? score.score))}</span>
          </span>
        )}
      </div>
      {score && scoreNote(score) && !sameAsHeadline && <p class="hs-deb-body hs-deb-note">{scoreNote(score)}</p>}
      {score && <p class="hs-deb-body">{scoreReasons(score)}</p>}
      {lines && (
        <>
          {(() => { const r = rise(); return <p class={cls('hs-deb-main', r)} style={r.style}>{lines.headline}</p> })()}
          {lines.wins.map((x) => { const r = rise(); return <p class={cls('hs-deb-line hs-deb-good', r)} style={r.style} key={x}>{`• ${x}`}</p> })}
          {lines.watch.map((x) => { const r = rise(); return <p class={cls('hs-deb-line hs-deb-gold', r)} style={r.style} key={x}>{`• ${x}`}</p> })}
          {lines.next.map((x) => { const r = rise(); return <p class={cls('hs-deb-line hs-deb-next', r)} style={r.style} key={x}>{x}</p> })}
          {lines.sameRest && <p class="hs-deb-line hs-deb-body">{lines.sameRest}</p>}
          {lines.footnote && <p class="hs-deb-foot">{lines.footnote}</p>}
        </>
      )}
      {canDebrief(settings) && <Button variant="tonal" onClick={ask}>{t('debrief_ask_coach')}</Button>}
    </div>
  )
}
