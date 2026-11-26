import { useEffect, useState } from 'preact/hooks'
import { listSep, plural, t, useLang, intlLocale } from '../../i18n'
import { debriefLines } from '../../data/debrief-text'
import type { BadgeDef } from '../../domain/badges'
import { MILESTONE_KINDS, SCORE_TIER_KEY } from '../../domain/finish-summary'
import { formatWeight } from '../../domain/units'
import { GRACE_MS } from '../../domain/workout-end'
import { formatWorkoutRecap } from '../../domain/workout-recap'
import { Button, toast } from '../kit'
import { CalendarIcon } from '../icons'
import { TrophyIcon } from '../home/glyphs'
import { dayName } from '../workouts/helpers'
import { prValueText, renderInsightText, type WorkoutSummaryData } from './summary'

const tr = (key: string, fallback: string) => { const v = t(key); return v === key ? fallback : v }
const badgeTitle = (b: BadgeDef) => tr(`badge_${b.id}_title`, b.title)

async function shareRecapText(title: string, text: string): Promise<void> {
  if (typeof navigator !== 'undefined' && typeof navigator.share === 'function') {
    try {
      await navigator.share({ title, text })
      return
    } catch (e) {
      if (e instanceof Error && e.name === 'AbortError') return
    }
  }
  try {
    await navigator.clipboard?.writeText(text)
    toast(t('live41_share_copied'))
  } catch {
    toast(t('live41_share_copied'))
  }
}

/** Editorial "Workout complete" screen (Android WorkoutCompleteScreen v3.5). */
export function WorkoutSummary({ sum, onDone }: { sum: WorkoutSummaryData; onDone: () => void; onView?: () => void }) {
  const lang = useLang()
  const arabic = lang === 'ar'
  const [filled, setFilled] = useState(false)
  useEffect(() => { const id = requestAnimationFrame(() => setFilled(true)); return () => cancelAnimationFrame(id) }, [])

  const unit = sum.unit ?? 'KG'
  const names = sum.exerciseNames ?? new Map<string, string>()
  const leveledUp = sum.after.level > sum.before.level
  const from = leveledUp ? 0 : sum.before.progress
  const pct = Math.round((filled ? sum.after.progress : from) * 100)
  const badges = sum.justLog ? [] : sum.newBadges
  const type = sum.dayType ?? 'ORDINARY'
  const insights = sum.insights ?? []
  const milestone = type === 'MILESTONE' ? insights.find((i) => MILESTONE_KINDS.has(i.kind)) ?? null : null
  const extraInsights = insights.filter((i) => i !== milestone).slice(0, 1)

  const lines = sum.debrief
    ? debriefLines(sum.debrief, lang, (id) => names.get(id) ?? id, (kg) => formatWeight(unit, kg, arabic))
    : null
  const headlineKey = !sum.justLog && sum.debrief && sum.headlineVariant != null
    ? `insight_h_${sum.debrief.headline.type.toLowerCase()}_${sum.headlineVariant + 1}`
    : null
  const headline = sum.justLog ? null
    : headlineKey && t(headlineKey, sum.debrief?.headline.up ?? 0, sum.debrief?.headline.compared ?? 0) !== headlineKey
      ? t(headlineKey, sum.debrief?.headline.up ?? 0, sum.debrief?.headline.compared ?? 0)
      : lines?.headline ?? null

  const hero = sum.prHero ?? null
  const heroVal = hero ? prValueText(unit, arabic, hero.kind, hero.weightKg, hero.reps, hero.seconds) : null
  const heroOld = hero ? prValueText(unit, arabic, hero.kind, hero.oldKg, hero.oldReps, hero.oldSeconds) : null
  const score = sum.score ?? null
  const showScore = sum.tier != null && score != null && score.showNumber && type !== 'FIRST_OR_BACK' && type !== 'ROUGH'

  const handleShare = () => {
    if (!sum.recap) return
    const text = formatWorkoutRecap(sum.recap, {
      setsLabel: (n) => plural('live41_recap_sets', n, n),
      minutesLabel: (m) => t('live41_recap_minutes', m),
      totalLiftedLabel: (total) => t('live41_recap_total_lifted', total),
      bestLabel: (best) => t('live41_recap_best', best),
      prTag: t('live41_recap_pr_tag'),
      prsHeaderLabel: (list) => t('live41_recap_prs_header', list),
      repsLabel: (r) => plural('debrief_n_reps', r, r),
      secondsLabel: (s) => plural('debrief_n_seconds', s, s),
      listSeparator: listSep(lang),
    })
    void shareRecapText(sum.recap.title, text)
  }

  return (
    <div class="shell lw lws" role="status">
      <div class="scroll lws-body lws-ed">
        <div class="lws-ed-kicker">{t('live_complete')}</div>
        <p class="lws-ed-sub">{sum.title}</p>

        {type === 'PR' && hero ? (
          <div class="lws-pr-hero">
            <div class="lws-pr-name">{names.get(hero.exerciseId) ?? hero.exerciseId}</div>
            {heroVal && <div class="lws-pr-val">{heroVal}</div>}
            {heroOld && <div class="lws-pr-was">{t('finish_pr_was', heroOld)}</div>}
            {headline && <p class="lws-comp">{headline}</p>}
          </div>
        ) : milestone ? (
          <>
            <h2 class="lws-headline">{renderInsightText(milestone, lang, names, unit)}</h2>
            {headline && <p class="lws-comp">{headline}</p>}
          </>
        ) : (
          <>
            {headline && <h2 class="lws-headline">{headline}</h2>}
            {type === 'ORDINARY' && score && score.compared > 0 && (
              <p class="lws-comp">{t('score_up', score.up, score.compared)}</p>
            )}
          </>
        )}

        {showScore && (
          <div class="lws-tier">
            <span class="lws-tier-word">{t(SCORE_TIER_KEY[sum.tier!])}</span>
            <span class="lws-tier-num">{t('debrief_score', score!.score)}</span>
          </div>
        )}

        {lines && (
          <div class="lws-lines">
            {lines.wins.map((x) => <div class="lws-win" key={x}>{`• ${x}`}</div>)}
            {lines.watch.map((x) => <div class="lws-watch" key={x}>{`• ${x}`}</div>)}
            {lines.next.map((x) => <div class="lws-next" key={x}>{x}</div>)}
            {lines.sameRest && <div class="lws-muted">{lines.sameRest}</div>}
          </div>
        )}

        {badges.map((b) => (
          <div class="lws-line lws-badge" style={{ marginBlockStart: '8px' }} key={b.id}>
            <span class="lws-trophy"><TrophyIcon size={20} /></span>
            <span>{t('live_badge_unlocked', badgeTitle(b))}</span>
          </div>
        ))}

        {extraInsights.length > 0 && (
          <div class="lws-insights">
            {extraInsights.map((ins) => <div key={ins.kind}>{renderInsightText(ins, lang, names, unit)}</div>)}
          </div>
        )}

        {lines?.footnote && <p class="lws-xpline" style={{ marginBlockStart: '12px' }}>{lines.footnote}</p>}

        {sum.nextUp && (
          <div class="lws-line" style={{ marginBlockStart: '16px' }}>
            <span class="lws-cal"><CalendarIcon size={20} /></span>
            <span>{t('live_next_up', dayName(sum.nextUp.dow, lang), sum.nextUp.title)}</span>
          </div>
        )}

        <div class="lws-meta">
          {[plural('hist_sets', sum.doneSets, sum.doneSets), t('hist_minutes', sum.minutes)].join(' · ')}
        </div>
        {sum.clippedEndAt != null && (
          <p class="lws-xpline">{t('live_ended_at_last_set', new Date(sum.clippedEndAt - GRACE_MS).toLocaleTimeString(intlLocale(lang), { hour: 'numeric', minute: '2-digit' }))}</p>
        )}
      </div>

      <div class="lws-foot">
        {!sum.justLog && (
          <div>
            <div class="lws-levelrow">
              <span class={`lws-level${leveledUp ? ' lws-levelup' : ''}`}>{leveledUp ? t('live_level_up', sum.after.level) : t('live_level', sum.after.level)}</span>
              <span class="lws-xpline">{plural('live_xp_gained', sum.xpGained, sum.xpGained)}</span>
            </div>
            <div class="lws-bar" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={pct}><div style={{ inlineSize: `${pct}%` }} /></div>
            <p class="lws-xpline">{t('profile_xp_line', sum.after.xpIntoLevel, sum.after.xpForNextLevel, sum.after.level + 1)}</p>
          </div>
        )}
        {sum.recap && <Button variant="tonal" block onClick={handleShare}>{t('live41_share_summary')}</Button>}
        <Button block onClick={onDone}>{t('home_done')}</Button>
      </div>
    </div>
  )
}
