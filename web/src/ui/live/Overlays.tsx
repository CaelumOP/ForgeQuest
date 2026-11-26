import { useEffect, useRef, useState } from 'preact/hooks'
import { t } from '../../i18n'
import { Button, reducedMotion } from '../kit'
import { CloseIcon } from '../icons'
import { clock, holdElapsed, holdPhase, restLeft, restWaveProgress, type HoldTimer, type RestTimer } from './timers'

import { restWaveTaper, restWaveTrackStart } from './restWave'

const REST_PRESETS = [60, 90, 120, 180] as const

export function RestBar({
  rest,
  now,
  nextLabel,
  canCopyLastSet,
  showPresets,
  onTogglePresets,
  onAdd,
  onRemove,
  onSelectPreset,
  onCopyLastSet,
  onSkip,
}: {
  rest: RestTimer
  now: number
  nextLabel: string | null
  canCopyLastSet: boolean
  showPresets: boolean
  onTogglePresets: () => void
  onAdd: () => void
  onRemove: () => void
  onSelectPreset: (seconds: number) => void
  onCopyLastSet: () => void
  onSkip: () => void
}) {
  const left = restLeft(rest.endsAt, now)
  const presets = rest.isRestPause ? [15, ...REST_PRESETS] : [...REST_PRESETS]
  return (
    <div class="lw-rest" role="timer" aria-label={t('live_rest')}>
      <div class="lw-rest-row">
        <button
          type="button"
          class="lw-rest-toggle"
          aria-expanded={showPresets}
          aria-label={t(showPresets ? 'cd_rest_presets_hide' : 'cd_rest_presets_show')}
          onClick={onTogglePresets}
        >
          <span class="lw-rest-title">{t('live_rest')}</span>
          <span class="lw-rest-clock">{clock(left)}</span>
        </button>
        <Button variant="text" onClick={onRemove}>{t('live_rest_minus')}</Button>
        <Button variant="text" onClick={onAdd}>{t('live_rest_plus')}</Button>
        <Button variant="text" onClick={onSkip}>{t('live_skip')}</Button>
      </div>
      {nextLabel && <div class="lw-rest-next">{t('live_next', nextLabel)}</div>}
      {showPresets && (
        <div class="lw-rest-presets">
          {presets.map((preset) => (
            <button
              key={preset}
              type="button"
              class={`lw-rest-chip${rest.totalSeconds === preset ? ' lw-rest-chip-on' : ''}`}
              onClick={() => onSelectPreset(preset)}
            >
              {t('live41_rest_preset_s', preset)}
            </button>
          ))}
          {canCopyLastSet && (
            <button type="button" class="lw-rest-chip lw-rest-chip-copy" onClick={onCopyLastSet}>
              {t('live41_copy_last_set')}
            </button>
          )}
        </div>
      )}
      <WaveBar endsAt={rest.endsAt} totalSeconds={rest.totalSeconds} />
    </div>
  )
}

// Match Android Material wave: 4 px strokes, 6 px visible gap, 32 px wavelength, 16 px/s.
const WAVE_STROKE = 4
const WAVE_LEN = 32
const WAVE_AMP = 3
const WAVE_CYCLES_PER_SEC = 0.5
const WAVE_H = 12
const WAVE_STEP = 1

function useReducedMotionPref(): boolean {
  const [reduced, setReduced] = useState(reducedMotion)
  useEffect(() => {
    if (typeof matchMedia !== 'function') return
    const mq = matchMedia('(prefers-reduced-motion: reduce)')
    setReduced(mq.matches)
    const onChange = () => setReduced(mq.matches)
    mq.addEventListener?.('change', onChange)
    return () => mq.removeEventListener?.('change', onChange)
  }, [])
  return reduced
}

/**
 * Calmer 60 FPS wavy rest countdown driven directly from `endsAt` via requestAnimationFrame,
 * with a separated flat track. Under prefers-reduced-motion renders a flat line whose progress still updates.
 */
function WaveBar({ endsAt, totalSeconds }: { endsAt: number; totalSeconds: number }) {
  const ref = useRef<SVGSVGElement>(null)
  const [w, setW] = useState(0)
  const reduced = useReducedMotionPref()
  const [frame, setFrame] = useState(() => ({
    progress: restWaveProgress(endsAt, totalSeconds, Date.now()),
    phaseRad: 0,
  }))

  useEffect(() => {
    const el = ref.current
    if (!el) return
    setW(el.getBoundingClientRect().width)
    if (typeof ResizeObserver === 'undefined') return
    const ro = new ResizeObserver(() => setW(el.getBoundingClientRect().width))
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  useEffect(() => {
    const initialProgress = restWaveProgress(endsAt, totalSeconds, Date.now())
    setFrame((prev) => ({ ...prev, progress: initialProgress }))
    if (initialProgress <= 0 || typeof requestAnimationFrame !== 'function') return

    let raf = 0
    let lastFrameMs = -1
    let cancelled = false
    const twoPi = Math.PI * 2

    const step = (frameMs: number) => {
      if (cancelled) return
      const nextProgress = restWaveProgress(endsAt, totalSeconds, Date.now())
      const dtSec = !reduced && nextProgress > 0 && lastFrameMs >= 0
        ? Math.min(0.1, Math.max(0, (frameMs - lastFrameMs) / 1000))
        : 0
      lastFrameMs = frameMs
      setFrame((prev) => ({
        progress: nextProgress,
        phaseRad: dtSec > 0 ? (prev.phaseRad + dtSec * WAVE_CYCLES_PER_SEC * twoPi) % twoPi : prev.phaseRad,
      }))
      if (nextProgress > 0) raf = requestAnimationFrame(step)
    }

    raf = requestAnimationFrame(step)
    return () => {
      cancelled = true
      if (typeof cancelAnimationFrame === 'function') cancelAnimationFrame(raf)
    }
  }, [endsAt, totalSeconds, reduced])

  const pad = WAVE_STROKE / 2
  const usableW = Math.max(0, w - WAVE_STROKE)
  const cy = WAVE_H / 2
  const activeW = usableW * Math.max(0, Math.min(1, frame.progress))
  const hasActive = w > WAVE_STROKE && activeW > 0
  const trackStart = restWaveTrackStart(usableW, frame.progress)

  let wave = ''
  if (hasActive && !reduced) {
    const maxAmp = Math.max(0, (WAVE_H - WAVE_STROKE) / 2)
    const amp = Math.min(WAVE_AMP, maxAmp)
    const twoPi = Math.PI * 2
    const pts: string[] = []
    for (let d = 0; d < activeW; d += WAVE_STEP) {
      const x = pad + d
      const y = cy + amp * restWaveTaper(d, activeW) * Math.sin((d / WAVE_LEN) * twoPi - frame.phaseRad)
      pts.push(`${pts.length === 0 ? 'M' : 'L'}${x.toFixed(2)} ${y.toFixed(2)}`)
    }
    const endX = pad + activeW
    const endY = cy
    pts.push(`${pts.length === 0 ? 'M' : 'L'}${endX.toFixed(2)} ${endY.toFixed(2)}`)
    wave = pts.join(' ')
  }

  return (
    <svg ref={ref} class="lw-wavebar" width="100%" height={WAVE_H} aria-hidden="true">
      {trackStart !== null && <>
        <line class="lw-wave-track" x1={pad + trackStart} x2={w - pad} y1={cy} y2={cy} />
        <circle class="lw-wave-stop" cx={w - pad} cy={cy} r={WAVE_STROKE / 2} />
      </>}
      {hasActive && (
        <>
          {!reduced && <path class="lw-wave" d={wave} />}
          <line class={`lw-wave-flat${reduced ? ' lw-wave-flat-on' : ''}`} x1={pad} x2={pad + activeW} y1={cy} y2={cy} />
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
