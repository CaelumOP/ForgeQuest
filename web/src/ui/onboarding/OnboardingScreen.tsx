import { useState } from 'preact/hooks'
import { getLang, setLang, t, type Lang } from '../../i18n'
import { updateSettings } from '../../data/settings'
import { logWeight } from '../../data/progress'
import { applyPlan, seedLibrary } from '../../data/workouts'
import { EXERCISE_CATALOG } from '../../data/seed/exercise-catalog'
import { STARTER_PLANS, localizePlan, type PlanDraft } from '../../data/seed/starter-plans'
import { todayEpochDay } from '../../domain/dates'
import { EQUIPMENT_PRESETS, PRESET_INFO, adaptPlanToEquipment, presetEquipment } from '../../domain/exercise-swap-engine'
import { MIN_SAFE_KCAL, NUTRITION_GOALS } from '../../domain/nutrition'
import { bodyStep, fromKg, toKg, unitLabel, type WeightUnit } from '../../domain/units'
import { Button, Card, ChipGroup, NumberField, Stat, Stepper, TextField } from '../kit'
import { CheckCircleIcon, OpenIcon, SparkleIcon } from '../home/glyphs'
import { LAST_STEP, bodyStatsReady, initialState, setWeight, settingsPatch, withSuggestions, type OnbState } from './logic'
import './onboarding.css'

const GEMINI_MODELS = 'https://generativelanguage.googleapis.com/v1beta/models?pageSize=1'
type KeyCheck = { busy: boolean; ok: boolean; msg: string | null }

/** Straight from the browser, like the AI check screen. Only a working key is saved. */
async function checkKey(key: string): Promise<KeyCheck> {
  try {
    const r = await fetch(GEMINI_MODELS, { headers: { 'x-goog-api-key': key } })
    if (r.ok) return { busy: false, ok: true, msg: t('onb_key_works') }
    return { busy: false, ok: false, msg: r.status === 400 || r.status === 401 || r.status === 403 ? t('onb_web_key_rejected') : t('onb_key_check_failed') }
  } catch {
    return { busy: false, ok: false, msg: t('onb_key_check_failed') }
  }
}

const emptyPlan = (): PlanDraft => ({
  name: t('onb_my_plan'), description: t('onb_my_plan_desc'),
  days: [1, 2, 3, 4, 5, 6, 7].map((d) => ({ dayOfWeek: d, title: t('today_rest_day'), isRest: true, note: '', items: [] })),
})

const trainingDaysOf = (i: number) => STARTER_PLANS[i]?.days.filter((d) => !d.isRest && d.items.length > 0).length ?? 3

/** First-run setup: language, name, goals, starter plan, optional Gemini key. Full screen, no tab bar. */
export function OnboardingScreen({ onDone }: { onDone: () => void }) {
  const [s, setS] = useState<OnbState>(initialState)
  const [key, setKey] = useState('')
  const [check, setCheck] = useState<KeyCheck>({ busy: false, ok: false, msg: null })
  const [saving, setSaving] = useState(false)
  const update = (f: (c: OnbState) => OnbState) => setS(f)
  const go = (d: number) => update((c) => ({ ...c, step: Math.min(LAST_STEP, Math.max(0, c.step + d)) }))

  const finish = async () => {
    if (saving) return
    setSaving(true)
    try {
      await seedLibrary(getLang())
      const chosen = STARTER_PLANS[s.planIndex]
      const plan = chosen ? localizePlan(chosen, getLang()) : emptyPlan()
      const adapted = chosen ? adaptPlanToEquipment(plan, new Map(EXERCISE_CATALOG.map((e) => [e.id, e])), presetEquipment(s.preset))[0] : plan
      await applyPlan(adapted, chosen ? 'starter' : 'manual')
      await logWeight(s.weightKg)
      await updateSettings(settingsPatch(s, todayEpochDay(), new Date().getFullYear(), check.ok ? key.trim() : null))
      onDone()
    } catch (e) {
      console.error('onboarding failed', e)
      setSaving(false)
    }
  }

  const runCheck = async () => {
    setCheck({ busy: true, ok: false, msg: null })
    setCheck(await checkKey(key.trim()))
  }

  return (
    <main class="onb">
      <div class="onb-top">
        <div class="onb-bar" role="progressbar" aria-valuemin={1} aria-valuemax={LAST_STEP + 1} aria-valuenow={s.step + 1} aria-label={t('onb_web_step', s.step + 1, LAST_STEP + 1)}>
          <div class="onb-bar-fill" style={{ inlineSize: `${((s.step + 1) / (LAST_STEP + 1)) * 100}%` }} />
        </div>
      </div>
      <div class="onb-body" key={s.step}>
        {s.step === 0 && <StepLanguage onNext={() => go(1)} />}
        {s.step === 1 && <StepWelcome s={s} update={update} />}
        {s.step === 2 && <StepGoals s={s} update={update} />}
        {s.step === 3 && <StepPlan s={s} update={update} />}
        {s.step === 4 && (
          <StepKey value={key} onInput={(v) => { setKey(v.trim()); setCheck({ busy: false, ok: false, msg: null }) }} check={check} onCheck={runCheck} />
        )}
      </div>
      {s.step > 0 && (
        <div class="onb-foot">
          <Button variant="text" onClick={() => go(-1)}>{t('action_back')}</Button>
          <span class="onb-spacer" />
          {s.step < LAST_STEP
            ? <Button onClick={() => go(1)} disabled={(s.step === 1 && !s.name.trim()) || (s.step === 2 && !bodyStatsReady(s))}>{t('action_continue')}</Button>
            : <Button onClick={finish} loading={saving}>{check.ok ? t('onb_lets_go') : t('onb_skip')}</Button>}
        </div>
      )}
    </main>
  )
}

function Title({ title, body }: { title: string; body: string }) {
  return (
    <header class="onb-title">
      <h1>{title}</h1>
      <p>{body}</p>
    </header>
  )
}

/** Both languages side by side: the person may not read the phone's language yet. */
function StepLanguage({ onNext }: { onNext: () => void }) {
  const pick = (l: Lang) => { setLang(l); onNext() }
  return (
    <div class="onb-center">
      <h1 class="onb-lang-title" lang="en" dir="ltr">Choose your language</h1>
      <h1 class="onb-lang-title" lang="ar" dir="rtl">اختر لغتك</h1>
      <div class="onb-lang-btns">
        <Button block onClick={() => pick('en')}>English</Button>
        <Button block onClick={() => pick('ar')}>العربية</Button>
      </div>
    </div>
  )
}

type StepProps = { s: OnbState; update: (f: (c: OnbState) => OnbState) => void }

function StepWelcome({ s, update }: StepProps) {
  return (
    <>
      <Title title={t('onb_welcome_title')} body={t('onb_welcome_body')} />
      <TextField label={t('onb_your_name')} value={s.name} autocomplete="given-name" onInput={(v) => update((c) => ({ ...c, name: v.slice(0, 24) }))} />
      <h2 class="onb-h2">{t('onb_body_for_map')}</h2>
      <ChipGroup label={t('onb_body_for_map')} value={s.bodyType} onChange={(v) => update((c) => ({ ...c, bodyType: v }))}
        options={[{ value: 'MALE', label: t('body_male') }, { value: 'FEMALE', label: t('body_female') }]} />
    </>
  )
}

function Row({ label, children }: { label: string; children: preact.ComponentChildren }) {
  return <div class="onb-row"><span class="onb-row-label">{label}</span>{children}</div>
}

function StepGoals({ s, update }: StepProps) {
  const ar = getLang() === 'ar'
  const unit = s.unit
  const shownWeight = Math.round(fromKg(unit, s.weightKg) * 10) / 10
  const setUnit = (u: WeightUnit) => update((c) => ({ ...c, unit: u }))
  return (
    <>
      <Title title={t('onb_goals_title')} body={t('onb_goals_body')} />
      <h2 class="onb-h2">{t('goal_title')}</h2>
      <ChipGroup label={t('goal_title')} value={s.goal} onChange={(g) => update((c) => withSuggestions({ ...c, goal: g }, trainingDaysOf))}
        options={NUTRITION_GOALS.map((g) => ({ value: g, label: t(`goal_${g.toLowerCase()}`) }))} />
      <ChipGroup label={t('weight_body')} value={unit} onChange={setUnit}
        options={[{ value: 'KG', label: t('unit_kilograms') }, { value: 'LB', label: t('unit_pounds') }]} />
      <Row label={t('weight_body')}>
        <Stepper label={t('weight_body')} value={shownWeight} step={bodyStep(unit)} min={fromKg(unit, 30)} max={fromKg(unit, 250)}
          format={(n) => `${n.toFixed(1)} ${unitLabel(unit, ar)}`} onChange={(v) => update((c) => setWeight(c, toKg(unit, v), trainingDaysOf))} />
      </Row>
      <div class="onb-pair">
        <NumberField label={t('profile_height')} unit={t('unit_cm')} hint={t('onb_web_height_hint')} value={s.heightCm || null} min={120} max={230}
          onChange={(v) => update((c) => withSuggestions({ ...c, heightCm: v ?? 0 }, trainingDaysOf))} />
        <NumberField label={t('profile_age')} unit={t('unit_years')} value={s.age || null} min={10} max={100}
          onChange={(v) => update((c) => withSuggestions({ ...c, age: v ?? 0 }, trainingDaysOf))} />
      </div>
      <Target s={s} update={update} />
    </>
  )
}

/** The calculated calories and protein, read-only until the person taps Adjust. */
function Target({ s, update }: StepProps) {
  const [editing, setEditing] = useState(false)
  if (!bodyStatsReady(s)) return <p class="onb-note">{t('onb_web_target_missing')}</p>
  const num = (n: number) => new Intl.NumberFormat(getLang()).format(n)
  return (
    <Card>
      <div class="onb-target-head">
        <h2 class="onb-h2">{t('onb_web_target_title')}</h2>
        {!editing && <Button variant="text" onClick={() => setEditing(true)}>{t('onb_web_adjust')}</Button>}
      </div>
      <div class="onb-target-stats">
        <Stat value={`${num(s.kcal)} ${t('unit_kcal')}`} label={t('profile_daily_calories')} accent="blue" />
        <Stat value={`${num(s.protein)} ${t('unit_g')}`} label={t('profile_daily_protein')} accent="teal" />
      </div>
      {editing && (
        <>
          <Row label={t('profile_daily_calories')}>
            <Stepper label={t('profile_daily_calories')} value={s.kcal} step={50} min={MIN_SAFE_KCAL} max={6000} format={(n) => `${num(n)}`}
              onChange={(v) => update((c) => ({ ...c, kcal: v, goalsTouched: true }))} />
          </Row>
          <Row label={t('profile_daily_protein')}>
            <Stepper label={t('profile_daily_protein')} value={s.protein} step={5} min={60} max={400} format={(n) => `${n} ${t('unit_g')}`}
              onChange={(v) => update((c) => ({ ...c, protein: v, goalsTouched: true }))} />
          </Row>
        </>
      )}
      <p class="onb-note">{s.goalsTouched ? t('onb_web_target_custom') : t('onb_web_target_body', trainingDaysOf(s.planIndex))}</p>
    </Card>
  )
}

function StepPlan({ s, update }: StepProps) {
  const ar = getLang() === 'ar'
  const plans = STARTER_PLANS.map((p) => localizePlan(p, getLang()))
  const options = [...plans.map((p) => ({ name: p.name, desc: p.description })), { name: t('onb_start_empty'), desc: t('onb_start_empty_desc') }]
  return (
    <>
      <Title title={t('onb_plan_title')} body={t('onb_plan_body')} />
      <h2 class="onb-h2">{t('onb_available_equipment')}</h2>
      <ChipGroup label={t('onb_available_equipment')} value={s.preset} onChange={(p) => update((c) => ({ ...c, preset: p }))}
        options={EQUIPMENT_PRESETS.map((p) => ({ value: p, label: ar ? PRESET_INFO[p].labelAr : PRESET_INFO[p].labelEn }))} />
      <div class="onb-plans" role="radiogroup" aria-label={t('onb_plan_title')}>
        {options.map((o, i) => {
          const on = s.planIndex === i
          return (
            <button key={i} type="button" role="radio" aria-checked={on} class={`onb-plan${on ? ' on' : ''}`}
              onClick={() => update((c) => withSuggestions({ ...c, planIndex: i, preset: i === 2 ? 'FULL_GYM' : c.preset }, trainingDaysOf))}>
              <span class="onb-plan-text">
                <span class="onb-plan-name">{o.name}</span>
                <span class="onb-plan-desc">{o.desc}</span>
              </span>
              <span class={`onb-radio${on ? ' on' : ''}`} aria-label={on ? t('onb_web_plan_selected') : undefined}>{on && <CheckCircleIcon />}</span>
            </button>
          )
        })}
      </div>
    </>
  )
}

function StepKey({ value, onInput, check, onCheck }: { value: string; onInput: (v: string) => void; check: KeyCheck; onCheck: () => void }) {
  return (
    <>
      <div class="onb-optional">
        <span class="onb-badge"><SparkleIcon /></span>
        <span>{t('onb_optional')}</span>
      </div>
      <Title title={t('onb_ai_title')} body={t('onb_ai_body')} />
      <ol class="onb-steps">
        {['onb_ai_step1', 'onb_ai_step2', 'onb_ai_step3'].map((k, i) => (
          <li key={k}><span class="onb-num" aria-hidden="true">{new Intl.NumberFormat(getLang()).format(i + 1)}</span><span>{t(k)}</span></li>
        ))}
      </ol>
      <a class="kit-btn kit-btn-tonal onb-getkey" href="https://aistudio.google.com/apikey" target="_blank" rel="noopener noreferrer">
        <OpenIcon size={18} /><span class="kit-btn-label">{t('onb_get_key')}</span>
      </a>
      <TextField label={t('onb_key_label')} type="password" value={value} autocomplete="off" onInput={onInput} />
      <div class="onb-check">
        <Button onClick={onCheck} loading={check.busy} disabled={!value}>{check.busy ? t('onb_checking') : t('onb_check_key')}</Button>
        {check.msg && <span class={`onb-msg ${check.ok ? 'ok' : 'bad'}`} role="status">{check.msg}</span>}
      </div>
      <p class="onb-note">{t('onb_privacy')}</p>
      <p class="onb-note">{t('onb_groq_hint')}</p>
    </>
  )
}
