// The one calorie card on Home (calorie plan v2): the one-time "more accurate target", the learned real burn, or a
// signal from the logs. Each has one main action and a quiet "Not now". Android: CalorieCards.kt.
import { useState } from 'preact/hooks'
import { t, intlLocale } from '../../i18n'
import { acceptCalorieCard, calorieCard, snoozeCalorieCards, suggestionFor, calorieInputs } from '../../data/calories'
import type { ProgressSnapshot } from '../../data/progress'
import { trainingDays } from '../../data/workouts'
import { Button, Card } from '../kit'

const nf = (n: number) => new Intl.NumberFormat(intlLocale()).format(n)

export function CalorieCard({ s, today }: { s: ProgressSnapshot; today: number }) {
  const [busy, setBusy] = useState(false)
  const card = calorieCard(s, today)
  if (!card || card.kind === 'STEP_DROP') return null // the web has no steps
  const run = async (job: () => Promise<unknown>) => { if (busy) return; setBusy(true); try { await job() } finally { setBusy(false) } }

  let title = ''
  let body = ''
  let main = ''
  let other = t('cal_web_not_now')
  if (card.kind === 'MIGRATION') {
    title = t('cal_web_v2_title'); body = t('cal_web_v2_body', nf(card.to), nf(card.from))
    main = t('cal_web_use', nf(card.to)); other = t('cal_web_keep', nf(card.from))
  } else if (card.kind === 'ADAPTIVE') {
    title = t('cal_web_adaptive_title'); body = t('cal_web_adaptive_body', nf(card.burn), nf(card.to)); main = t('cal_web_update')
  } else if (card.kind === 'UNDER_FUELLED') {
    const i = calorieInputs(s.settings, s.latestWeight, s.plan ? trainingDays(s.plan).size : 0)
    const to = i ? suggestionFor(i, { ...s.settings, cutPace: card.pace }) : s.settings.kcalGoal
    title = t('cal_web_underfuel_title'); body = t('cal_web_underfuel_body', nf(to))
    main = t('cal_web_switch', t(card.pace === 'GENTLE' ? 'pace_web_gentle' : 'pace_web_standard'))
  } else if (card.kind === 'WEIGHT_CHANGED') {
    title = t('cal_web_weight_title'); body = t('cal_web_weight_body', nf(card.to)); main = t('cal_web_use', nf(card.to))
  } else {
    title = t('cal_web_creep_title'); body = t('cal_web_creep_body', nf(card.to)); main = t('cal_web_use', nf(card.to))
  }

  return (
    <Card>
      <div class="home-cal">
        <div class="home-t">{title}</div>
        <p class="home-s">{body}</p>
        <div class="home-cal-actions">
          <Button variant="text" onClick={() => void run(() => snoozeCalorieCards(today, card.kind === 'MIGRATION'))}>{other}</Button>
          <Button variant="tonal" loading={busy} onClick={() => void run(() => acceptCalorieCard(card, s, today))}>{main}</Button>
        </div>
      </div>
    </Card>
  )
}
