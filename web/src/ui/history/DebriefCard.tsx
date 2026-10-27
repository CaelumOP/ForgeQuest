import { useMemo } from 'preact/hooks'
import { getLang, t } from '../../i18n'
import { canDebrief, askCoachAboutWorkout } from '../../data/coach-chat'
import { debriefLines } from '../../data/debrief-text'
import { useSettings, weightUnit } from '../../data/settings'
import { useLibrary, useWorkoutDebrief } from '../../data/workouts'
import { formatWeight } from '../../domain/units'
import type { WorkoutScoreResult } from '../../domain/workout-score'
import { Button } from '../kit'
import { scoreNote, scoreReasons, scoreTone } from './helpers'
import './history.css'

/**
 * The workout debrief, calculated from the person's numbers: the score with its reasons, then the headline, what went
 * well (teal), what to watch (gold) and next time (blue). The coach button opens the Coach with the workout's message.
 * `onAsked` runs after the message is left for the Coach (a sheet closes itself).
 */
export function DebriefCard({ sessionId, score, onAsked }: { sessionId: number; score?: WorkoutScoreResult | null; onAsked?: () => void }) {
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
  const ask = () => { void askCoachAboutWorkout(sessionId).then((ok) => { if (ok) { onAsked?.(); location.hash = '#/coach' } }) }
  return (
    <div class="hs-deb-card">
      <div class="hs-deb-head">
        <span class="hs-deb-title">{t('debrief_title')}</span>
        {score?.showNumber && <span class={`hs-deb-gold ${scoreTone(score.score)}`}>{t('debrief_score', score.score)}</span>}
      </div>
      {score && scoreNote(score) && !sameAsHeadline && <p class="hs-deb-body hs-deb-note">{scoreNote(score)}</p>}
      {score && <p class="hs-deb-body">{scoreReasons(score)}</p>}
      {lines && (
        <>
          <p class="hs-deb-main">{lines.headline}</p>
          {lines.wins.map((x) => <p class="hs-deb-line hs-deb-good" key={x}>{`• ${x}`}</p>)}
          {lines.watch.map((x) => <p class="hs-deb-line hs-deb-gold" key={x}>{`• ${x}`}</p>)}
          {lines.next.map((x) => <p class="hs-deb-line hs-deb-next" key={x}>{x}</p>)}
          {lines.sameRest && <p class="hs-deb-line hs-deb-body">{lines.sameRest}</p>}
          {lines.footnote && <p class="hs-deb-foot">{lines.footnote}</p>}
        </>
      )}
      {canDebrief(settings) && <Button variant="tonal" onClick={ask}>{t('debrief_ask_coach')}</Button>}
    </div>
  )
}
