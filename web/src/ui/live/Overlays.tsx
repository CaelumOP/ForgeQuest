import { useEffect, useId, useRef, useState } from 'preact/hooks'
import { t } from '../../i18n'
import { Button } from '../kit'
import { CloseIcon } from '../icons'
import { clock, holdElapsed, holdPhase, restLeft, type HoldTimer, type RestTimer } from './timers'

export function RestBar({ rest, now, nextLabel, onAdd, onRemove, onSkip }: { rest: RestTimer; now: number; nextLabel: string | null; onAdd: () => void; onRemove: () => void; onSkip: () => void }) {
  const left = restLeft(rest.endsAt, now)
  return (
    <div class="lw-rest" role="timer" aria-label={t('live_rest')}>
      <div class="lw-rest-row">
        <span class="lw-rest-title">{t('live_rest')}</span>
        <span class="lw-rest-clock">{clock(left)}</span>
        <Button variant="text" onClick={onRemove}>{t('live_rest_minus')}</Button>
        <Button variant="text" onClick={onAdd}>{t('live_rest_plus')}</Button>
        <Button variant="text" onClick={onSkip}>{t('live_skip')}</Button>
      </div>
      {nextLabel && <div class="lw-rest-next">{t('live_next', nextLabel)}</div>}
      <WaveBar progress={rest.totalSeconds ? Math.min(1, left / rest.totalSeconds) : 0} />
    </div>
  )
}

// Android LinearWavyProgressIndicator tokens: 4 px strokes, 3 px wave amplitude, 40 px wavelength, 4 px gap, 10 px tall.
const WAVE_LEN = 40
const WAVE_AMP = 3
const WAVE_GAP = 4
const WAVE_H = 10

/** The rest countdown as an Expressive wavy bar. Under prefers-reduced-motion the wave is swapped for a flat, still line (CSS). */
function WaveBar({ progress }: { progress: number }) {
  const ref = useRef<SVGSVGElement>(null)
  const clip = useId()
  const [w, setW] = useState(0)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    setW(el.getBoundingClientRect().width)
    if (typeof ResizeObserver === 'undefined') return
    const ro = new ResizeObserver(() => setW(el.getBoundingClientRect().width))
    ro.observe(el)
    return () => ro.disconnect()
  }, [])
  const active = w * Math.max(0, Math.min(1, progress))
  const cy = WAVE_H / 2
  // One half-wavelength per quadratic segment; the control point sits 2x the amplitude off the line, so the peak is the amplitude.
  let wave = `M ${-WAVE_LEN} ${cy} q ${WAVE_LEN / 4} ${-2 * WAVE_AMP} ${WAVE_LEN / 2} 0`
  for (let x = -WAVE_LEN / 2; x < active + WAVE_LEN; x += WAVE_LEN / 2) wave += ` t ${WAVE_LEN / 2} 0`
  const trackFrom = active > 0 ? active + WAVE_GAP + 2 : 2
  return (
    <svg ref={ref} class="lw-wavebar" width="100%" height={WAVE_H} aria-hidden="true">
      {w > 0 && trackFrom < w - 2 && <line class="lw-wave-track" x1={trackFrom} x2={w - 2} y1={cy} y2={cy} />}
      {w > 0 && active > 2 && (
        <>
          <clipPath id={clip}><rect x="0" y="0" width={active} height={WAVE_H} /></clipPath>
          <g clip-path={`url(#${clip})`}><path class="lw-wave" d={wave} /></g>
          <line class="lw-wave-flat" x1="2" x2={Math.max(2, active - 2)} y1={cy} y2={cy} />
        </>
      )}
    </svg>
  )
}

/** Full-screen clock for a timed hold: 3-2-1, the countdown to the target, then extra time counting up. */
export function HoldOverlay({ hold, now, onStop, onCancel }: { hold: HoldTimer; now: number; onStop: () => void; onCancel: () => void }) {
  const phase = holdPhase(hold, now)
  const s = holdElapsed(hold, now)
  const shown = phase === 'ready' ? String(-s) : phase === 'over' ? `+${clock(s - hold.target)}` : clock(hold.target - s)
  const tone = phase === 'ready' ? 'muted' : phase === 'over' ? 'gold' : 'teal'
  return (
    <div class="lw-hold" role="dialog" aria-modal="true" aria-label={hold.exerciseName}>
      <div class="lw-hold-head">
        <h2>{hold.exerciseName}</h2>
        <button type="button" class="icon-button" aria-label={t('action_cancel')} onClick={onCancel}><CloseIcon /></button>
      </div>
      <div class="lw-hold-mid">
        <div class={`lw-hold-label lw-tone-${tone}`}>{t(phase === 'ready' ? 'hold_get_ready' : phase === 'over' ? 'hold_overtime' : 'hold_holding')}</div>
        <div class={`lw-hold-clock lw-tone-${tone}`} aria-live="off">{shown}</div>
        <div class={`lw-bar lw-bar-${tone}`}><div style={{ inlineSize: `${phase === 'ready' ? 0 : Math.min(100, (s / hold.target) * 100)}%` }} /></div>
        <div class="lw-small">{t('hold_target', hold.target)}</div>
      </div>
      <div class="lw-hold-foot">
        <Button block onClick={phase === 'ready' ? onCancel : onStop}>{t(phase === 'over' ? 'hold_done' : 'hold_stop')}</Button>
        {phase === 'holding' && <p class="lw-small lw-center">{t('hold_stop_hint')}</p>}
      </div>
    </div>
  )
}
