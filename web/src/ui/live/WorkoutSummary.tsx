import { useEffect, useState } from 'preact/hooks'
import { t, useLang } from '../../i18n'
import type { BadgeDef } from '../../domain/badges'
import { Button, Card } from '../kit'
import { CalendarIcon, CheckIcon } from '../icons'
import { TrophyIcon } from '../home/glyphs'
import { dayName } from '../workouts/helpers'
import type { WorkoutSummaryData } from './summary'
import { scoreNote, scoreReasons, scoreTone } from '../history/helpers'

const tr = (key: string, fallback: string) => { const v = t(key); return v === key ? fallback : v }
const badgeTitle = (b: BadgeDef) => tr(`badge_${b.id}_title`, b.title)

/** "Workout complete" screen shown right after finishing (Android WorkoutCompleteScreen). */
export function WorkoutSummary({ sum, onDone, onView }: { sum: WorkoutSummaryData; onDone: () => void; onView: () => void }) {
  const lang = useLang()
  const [filled, setFilled] = useState(false)
  useEffect(() => { const id = requestAnimationFrame(() => setFilled(true)); return () => cancelAnimationFrame(id) }, [])
  const leveledUp = sum.after.level > sum.before.level
  const from = leveledUp ? 0 : sum.before.progress
  const pct = Math.round((filled ? sum.after.progress : from) * 100)
  const rankTitle = tr(`rank_${sum.after.rank.title.toLowerCase()}`, sum.after.rank.title)
  return (
    <div class="shell lw lws" role="status">
      <div class="scroll lws-body">
        <span class="lws-check"><CheckIcon size={44} /></span>
        <h2 class="lws-title">{t('live_complete')}</h2>
        <p class="lws-sub">{sum.title}</p>
        <div class="lws-stats">
          <div class="lws-stat" style={{ color: 'var(--teal)' }}><b>{sum.doneSets}</b><span>{t('live_sets')}</span></div>
          <div class="lws-stat" style={{ color: 'var(--blue)' }}><b>{t('hist_minutes', sum.minutes)}</b><span>{t('profile_time')}</span></div>
          <div class="lws-stat" style={{ color: 'var(--gold)' }}><b>+{sum.xpGained}</b><span>{t('live_xp')}</span></div>
        </div>
        {sum.score && (
          <Card class="lws-card">
            <div class="lws-levelrow">
              <span class="lws-level">{t('score_title')}</span>
              {sum.score.showNumber && <span class={`lws-score ${scoreTone(sum.score.score)}`}>{t('debrief_score', sum.score.score)}</span>}
            </div>
            {scoreNote(sum.score) && <p class="lws-xpline lws-scorenote">{scoreNote(sum.score)}</p>}
            <p class="lws-xpline">{scoreReasons(sum.score)}</p>
          </Card>
        )}
        <Card class="lws-card">
          <div class="lws-levelrow">
            <span class={`lws-level${leveledUp ? ' lws-levelup' : ''}`}>{leveledUp ? t('live_level_up', sum.after.level) : t('live_level', sum.after.level)}</span>
            <span class="lws-rank">{t('live_rank', sum.after.rank.letter, rankTitle)}</span>
          </div>
          <div class="lws-bar" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={pct}><div style={{ inlineSize: `${pct}%` }} /></div>
          <p class="lws-xpline">{t('profile_xp_line', sum.after.xpIntoLevel, sum.after.xpForNextLevel, sum.after.level + 1)}</p>
        </Card>
        {(sum.prs.length > 0 || sum.newBadges.length > 0) && (
          <Card class="lws-card">
            {sum.prs.map((name) => (
              <div class="lws-line" key={`pr-${name}`}><span class="lw-pr">{t('live_pr')}</span><span>{t('live_new_record', name)}</span></div>
            ))}
            {sum.newBadges.map((b) => (
              <div class="lws-line lws-badge" key={b.id}><span class="lws-trophy"><TrophyIcon size={20} /></span><span>{t('live_badge_unlocked', badgeTitle(b))}</span></div>
            ))}
          </Card>
        )}
        {sum.nextUp && (
          <Card class="lws-card">
            <div class="lws-line"><span class="lws-cal"><CalendarIcon size={20} /></span><span>{t('live_next_up', dayName(sum.nextUp.dow, lang), sum.nextUp.title)}</span></div>
          </Card>
        )}
        <div class="lws-actions">
          <Button block onClick={onDone}>{t('home_done')}</Button>
          <Button block variant="tonal" onClick={onView}>{t('live_web_view_workout')}</Button>
        </div>
      </div>
    </div>
  )
}
