import { useState } from 'preact/hooks'
import { t } from '../../i18n'
import { logWeight } from '../../data/progress'
import { updateSettings, type Settings } from '../../data/settings'
import { todayEpochDay } from '../../domain/dates'
import { unitFromCodeOrLabel } from '../../domain/units'
import { Button } from '../kit'
import { StepBody, StepGoal } from './OnboardingScreen'
import { bodyStatsReady, foodTargetsPatch, initialState, withSuggestions, type OnbState } from './logic'
import './onboarding.css'

/** The body and food-goal steps that used to sit in onboarding, offered from Home after the first workout. */
export function FoodSetup({ settings, latestWeight, onClose }: { settings: Settings; latestWeight: number | null; onClose: () => void }) {
  const [s, setS] = useState<OnbState>(() => withSuggestions({
    ...initialState(),
    step: 0,
    bodyType: settings.bodyType,
    unit: unitFromCodeOrLabel(settings.unit),
    weightKg: latestWeight ?? 75,
    daysPerWeek: settings.weeklyTargetOverride > 0 ? settings.weeklyTargetOverride : 3,
  }))
  const [saving, setSaving] = useState(false)
  const update = (f: (c: OnbState) => OnbState) => setS(f)

  const save = async () => {
    if (saving) return
    setSaving(true)
    try {
      await logWeight(s.weightKg)
      await updateSettings(foodTargetsPatch(s, todayEpochDay(), new Date().getFullYear()))
      onClose()
    } catch (e) {
      console.error('food setup failed', e)
      setSaving(false)
    }
  }

  return (
    <div class="onb" role="dialog" aria-modal="true" aria-label={t('food_setup_title')}>
      <div class="onb-top">
        <div class="onb-bar" role="progressbar" aria-valuemin={1} aria-valuemax={2} aria-valuenow={s.step + 1} aria-label={t('onb_web_step', s.step + 1, 2)}>
          <div class="onb-bar-fill" style={{ inlineSize: `${((s.step + 1) / 2) * 100}%` }} />
        </div>
      </div>
      <div class="onb-body" key={s.step}>
        {s.step === 0 ? <StepBody s={s} update={update} /> : <StepGoal s={s} update={update} />}
      </div>
      <div class="onb-foot">
        <Button variant="text" onClick={() => (s.step === 0 ? onClose() : update((c) => ({ ...c, step: 0 })))}>{s.step === 0 ? t('action_cancel') : t('action_back')}</Button>
        <span class="onb-spacer" />
        {s.step === 0
          ? <Button onClick={() => update((c) => ({ ...c, step: 1 }))} disabled={!bodyStatsReady(s)}>{t('action_continue')}</Button>
          : <Button onClick={save} loading={saving}>{t('action_save')}</Button>}
      </div>
    </div>
  )
}
