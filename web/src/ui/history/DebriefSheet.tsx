import { getLang } from '../../i18n'
import type { SessionRow } from '../../data/db'
import type { WorkoutScoreResult } from '../../domain/workout-score'
import { Sheet } from '../kit'
import { DebriefCard } from './DebriefCard'

/** The journal's debrief for one workout, calculated from its numbers; it changes when a set is edited. */
export function DebriefSheet({ session, score, onClose }: { session: SessionRow; score?: WorkoutScoreResult; onClose: () => void }) {
  const date = new Intl.DateTimeFormat(getLang(), { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' }).format(new Date(session.epochDay * 86_400_000))
  return (
    <Sheet open onClose={onClose} title={session.title}>
      <div class="hs-sheet">
        <p class="hs-deb-date">{date}</p>
        <DebriefCard sessionId={session.id!} score={score} onAsked={onClose} />
      </div>
    </Sheet>
  )
}
