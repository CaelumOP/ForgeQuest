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
      <div class="lw-bar lw-bar-blue"><div style={{ inlineSize: `${rest.totalSeconds ? Math.min(100, (left / rest.totalSeconds) * 100) : 0}%` }} /></div>
    </div>
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
