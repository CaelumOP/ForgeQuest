// Two one-time Home cards from the shorter onboarding: the food-targets setup (after the first workout) and the
// "make N your weekly target?" question inside the streak card. Android: SetupCards.kt.
import { useState } from 'preact/hooks'
import { plural, t } from '../../i18n'
import type { ProgressSnapshot } from '../../data/progress'
import { updateSettings } from '../../data/settings'
import { nudgeTarget, showTargetNudge } from '../../domain/target-nudge'
import { trainingDays } from '../../data/workouts'
import { fullTarget } from '../../domain/progress'
import { Button, Card } from '../kit'
import { FoodSetup } from '../onboarding/FoodSetup'
import { needsFoodTargets } from '../onboarding/logic'

export function FoodTargetsCard({ s }: { s: ProgressSnapshot }) {
  const [open, setOpen] = useState(false)
  if (!needsFoodTargets(s.settings.heightCm, s.sessions.length)) return null
  return (
    <>
      <Card onClick={() => setOpen(true)}>
        <div class="home-t">{t('food_setup_title')}</div>
        <p class="home-s">{t('food_setup_body')}</p>
      </Card>
      {open && <FoodSetup settings={s.settings} latestWeight={s.latestWeight} onClose={() => setOpen(false)} />}
    </>
  )
}

/** The target the person currently aims for: their own number, else the plan's training days. */
const currentTarget = (s: ProgressSnapshot): number => fullTarget(s.plan ? trainingDays(s.plan) : new Set<number>(), s.settings.weeklyTargetOverride)

export function TargetNudge({ s, today }: { s: ProgressSnapshot; today: number }) {
  const [busy, setBusy] = useState(false)
  const target = currentTarget(s)
  const workoutDays = new Set(s.sessions.map((x) => x.epochDay))
  const start = Math.min(workoutDays.size ? Math.min(...workoutDays) : today, today)
  const offer = nudgeTarget(workoutDays, target, start, today)
  if (!s.settings.onboarded || !showTargetNudge(s.settings.targetNudgeAnswered, offer)) return null
  const answer = async (make: boolean) => {
    if (busy) return
    setBusy(true)
    try { await updateSettings((c) => ({ ...c, targetNudgeAnswered: true, weeklyTargetOverride: make ? offer : c.weeklyTargetOverride })) } finally { setBusy(false) }
  }
  return (
    <div class="home-nudge" role="group">
      <p class="home-s">{plural('target_nudge_body', offer, offer)}</p>
      <div class="home-cal-actions">
        <Button variant="text" onClick={() => void answer(false)}>{t('target_nudge_keep', target)}</Button>
        <Button variant="tonal" loading={busy} onClick={() => void answer(true)}>{t('target_nudge_make', offer)}</Button>
      </div>
    </div>
  )
}
