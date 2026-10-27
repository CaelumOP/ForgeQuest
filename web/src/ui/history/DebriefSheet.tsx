import { useEffect, useState } from 'preact/hooks'
import { getLang, t } from '../../i18n'
import type { SessionRow } from '../../data/db'
import type { WorkoutScoreResult } from '../../domain/workout-score'
import { canDebrief, generateDebrief } from '../../data/coach-chat'
import { decodeDebriefFull } from '../../data/coach'
import { useSettings } from '../../data/settings'
import { Button, Sheet } from '../kit'
import { scoreReasons } from './helpers'

/** The journal's coach debrief for one workout: the saved one, or the coach writing it now, with Try again on failure. */
export function DebriefSheet({ session, score, onClose }: { session: SessionRow; score?: WorkoutScoreResult; onClose: () => void }) {
  const settings = useSettings()
  const debrief = decodeDebriefFull(session.debrief)
  const hasKey = canDebrief(settings)
  const [loading, setLoading] = useState(false)
  const [failed, setFailed] = useState(false)

  const write = () => {
    if (loading) return
    setLoading(true)
    setFailed(false)
    generateDebrief(session.id!, true)
      .then((d) => setFailed(d == null))
      .catch(() => setFailed(true))
      .finally(() => setLoading(false))
  }
  // A workout with no saved debrief gets one written as the sheet opens.
  useEffect(() => { if (!debrief && hasKey) write() }, [])

  const date = new Intl.DateTimeFormat(getLang(), { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' }).format(new Date(session.epochDay * 86_400_000))
  return (
    <Sheet open onClose={onClose} title={session.title}>
      <div class="hs-sheet">
        <p class="hs-deb-date">{date}</p>
        {score && (
          <div class="hs-deb-card">
            <div class="hs-deb-head">
              <span class="hs-deb-title">{t('score_title')}</span>
              <span class="hs-deb-gold">{t('debrief_score', score.score)}</span>
            </div>
            <p class="hs-deb-body">{scoreReasons(score)}</p>
          </div>
        )}
        {!debrief && !hasKey ? <p class="hs-deb-body">{t('debrief_no_key')}</p> : (
          <div class="hs-deb-card">
            <div class="hs-deb-head">
              <span class="hs-deb-title">{t('debrief_title')}</span>
            </div>
            {debrief ? (
              <>
                <p class="hs-deb-main">{debrief.summary}</p>
                {/* Teal for what went well, gold for what to watch, blue for next time. */}
                {debrief.highlights.map((x) => <p class="hs-deb-line hs-deb-good" key={x}>{`• ${x}`}</p>)}
                {debrief.watch.map((x) => <p class="hs-deb-line hs-deb-gold" key={x}>{`• ${x}`}</p>)}
                {debrief.nextTime.map((x) => <p class="hs-deb-line hs-deb-next" key={x}>{t('debrief_next_time', x)}</p>)}
              </>
            ) : failed && !loading ? (
              <>
                <p class="hs-deb-body">{t('debrief_failed')}</p>
                <Button variant="tonal" onClick={write}>{t('action_try_again')}</Button>
              </>
            ) : <p class="hs-deb-body" role="status">{t('debrief_loading')}</p>}
          </div>
        )}
      </div>
    </Sheet>
  )
}
