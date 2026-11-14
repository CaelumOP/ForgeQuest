// Port of app/src/main/java/com/forgequest/app/ui/plan/PlanBuilderScreen.kt.
import type { ComponentChildren } from 'preact'
import { useEffect, useMemo, useState } from 'preact/hooks'
import { intlLocale, t, useLang, type Lang } from '../../i18n'
import { loadSettings, updateSettings } from '../../data/settings'
import { applyPlan, useLibrary } from '../../data/workouts'
import type { ExerciseEntity, PlanDraftDay } from '../../domain/entities'
import {
  CARDIOS, EXPERIENCES, INJURIES, PlanBuilder, REP_PREFS, REST_PREFS, TRAIN_GOALS,
  defaultPlanAnswers, encodePlanAnswers, planAnswersToInput, spreadDays,
  type BuiltPlan, type Cardio, type Experience, type PlanAnswers, type RepPref, type RestPref, type TrainGoal,
} from '../../domain/plan-builder'
import { AddIcon, CheckIcon, CloseIcon, ExpandMoreIcon } from '../icons'
import { Button, Card, Chip, IconButton, Segmented, Stepper, haptics, toast } from '../kit'
import { goBack } from '../nav'
import { GearCustomizeRow, GearSheet, TrainingPlaceSelector } from '../profile/GearSheet'
import { ExercisePicker } from '../workouts/ExercisePicker'
import { SwapIcon } from '../workouts/glyphs'
import { dayName, dayShort, muscleLabel } from '../workouts/helpers'
import {
  DAY_COUNT_OPTIONS, FOCUS_MUSCLES, MINUTE_OPTIONS,
  cardioKey, dayFocusKey, equipmentSetting, experienceKey, goalDescKey, goalKey,
  initialAnswers, injuryKey, localizeBuiltPlan, repPrefKey, restPrefKey,
  rowSummaries, scheme, swapBuiltPlanItem, whyTagKey,
} from './helpers'
import './plan-builder.css'

const TUNE_PATH = 'M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z'
const REFRESH_PATH = 'M17.65 6.35A7.958 7.958 0 0012 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0112 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z'

const TuneIcon = ({ size = 18 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false"><path d={TUNE_PATH} /></svg>
)
const RefreshIcon = ({ size = 18 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false"><path d={REFRESH_PATH} /></svg>
)

export function PlanBuilderScreen(_: { param?: string }) {
  const lang = useLang()
  const library = useLibrary()
  const byId = useMemo(() => new Map<string, ExerciseEntity>(library.map((e) => [e.id, e])), [library])

  const [answers, setAnswers] = useState<PlanAnswers>(defaultPlanAnswers)
  const [openRow, setOpenRow] = useState<string>('')
  const [editDay, setEditDay] = useState<number>(0)
  const [avoiding, setAvoiding] = useState(false)
  const [review, setReview] = useState<BuiltPlan | null>(null)
  const [offset, setOffset] = useState(0)
  const [tried, setTried] = useState<Map<string, Set<string>>>(() => new Map())
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let active = true
    void loadSettings().then((s) => {
      if (active) setAnswers(initialAnswers(s))
    })
    return () => { active = false }
  }, [])

  const change = (fn: (a: PlanAnswers) => PlanAnswers) => {
    setAnswers((prev) => fn(prev))
  }

  const toggleRow = (id: string) => {
    setOpenRow((prev) => (prev === id ? '' : id))
  }

  const build = (different = false) => {
    if (answers.days.size === 0 || library.length === 0 || busy) return
    const nextOffset = different ? offset + 1 : 0
    setBusy(true)
    const built = PlanBuilder.build(planAnswersToInput(answers, nextOffset), library)
    setOffset(nextOffset)
    setTried(new Map())
    setReview(built)
    setBusy(false)
    document.querySelector('.scroll')?.scrollTo(0, 0) // the review starts at its first day, not where the answers were scrolled to
  }

  const swap = (dayOfWeek: number, index: number) => {
    if (!review) return
    const res = swapBuiltPlanItem(review, answers, tried, library, dayOfWeek, index)
    setReview(res.review)
    setTried(res.tried)
  }

  const use = async () => {
    if (!review || busy) return
    setBusy(true)
    try {
      await applyPlan(localizeBuiltPlan(review, answers.goal, t), 'builder')
      await updateSettings((s) => ({
        ...s,
        equipment: equipmentSetting(answers.equipment),
        planBuilderAnswers: encodePlanAnswers(answers),
        weeklyTargetOverride: 0,
      }))
      toast(t('pb_saved'))
      goBack()
    } finally {
      setBusy(false)
    }
  }

  return (
    <div class="content pb">
      <header class="pb-head">
        {review != null && <h1 class="pb-title">{t('pb_review_title')}</h1>}
        <p class="pb-sub">{t(review == null ? 'pb_quick_hint' : 'pb_review_hint')}</p>
      </header>

      {review == null ? (
        <>
          <QuickCard a={answers} lang={lang} change={change} />
          <FitMeSection
            a={answers}
            lang={lang}
            open={openRow}
            editDay={editDay}
            byId={byId}
            onToggle={toggleRow}
            onEditDay={setEditDay}
            onOpenAvoid={() => setAvoiding(true)}
            change={change}
          />
          <Button
            block
            class="pb-cta"
            icon={<TuneIcon size={18} />}
            disabled={answers.days.size === 0 || library.length === 0 || busy}
            loading={busy}
            onClick={() => build(false)}
          >
            {t(busy ? 'pb_building' : 'pb_build')}
          </Button>
        </>
      ) : (
        <ReviewSection
          review={review}
          byId={byId}
          lang={lang}
          busy={busy}
          onSwap={swap}
          onUse={use}
          onAnother={() => build(true)}
          onChange={() => setReview(null)}
        />
      )}

      <ExercisePicker
        open={avoiding}
        title={t('pb_avoid_title')}
        library={library}
        userEquipment={answers.equipment}
        onPick={(ex) => {
          change((it) => ({ ...it, avoidIds: new Set([...it.avoidIds, ex.id]) }))
          setAvoiding(false)
        }}
        onClose={() => setAvoiding(false)}
      />
    </div>
  )
}

const GOAL_PATHS: Record<TrainGoal, string> = {
  STRENGTH: 'M11 21h-1l1-7H7.5c-.58 0-.57-.32-.38-.66.19-.34.05-.08.07-.12C8.48 10.94 10.42 7.54 13 3h1l-1 7h3.5c.49 0 .56.33.47.51l-.07.15C12.96 17.55 11 21 11 21z',
  MUSCLE: 'M20.57 14.86L22 13.43 20.57 12 17 15.57 8.43 7 12 3.43 10.57 2 9.14 3.43 7.71 2 5.57 4.14 4.14 2.71 2.71 4.14l1.43 1.43L2 7.71l1.43 1.43L2 10.57 3.43 12 7 8.43 15.57 17 12 20.57 13.43 22l1.43-1.43L16.29 22l2.14-2.14 1.43 1.43 1.43-1.43-1.43-1.43L22 16.29z',
  FAT_LOSS: 'M13.5.67s.74 2.65.74 4.8c0 2.06-1.35 3.73-3.41 3.73-2.07 0-3.63-1.67-3.63-3.73l.03-.36C5.21 7.51 4 10.62 4 14c0 4.42 3.58 8 8 8s8-3.58 8-8C20 8.61 17.41 3.8 13.5.67z',
  ENDURANCE: 'M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z',
  GENERAL: 'M12 2C6.49 2 2 6.49 2 12s4.49 10 10 10 10-4.49 10-10S17.51 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm0-14c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6-2.69-6-6-6zm0 10c-2.21 0-4-1.79-4-4s1.79-4 4-4 4 1.79 4 4-1.79 4-4 4zm0-6a2 2 0 1 0 0 4 2 2 0 0 0 0-4z',
}

function TrainGoalSelector({ selected, onSelect }: { selected: TrainGoal; onSelect: (g: TrainGoal) => void }) {
  return (
    <div class="pb-goals" role="radiogroup" aria-label={t('pb_goal')}>
      {TRAIN_GOALS.map((g) => {
        const on = selected === g
        return (
          <button
            key={g}
            type="button"
            role="radio"
            aria-checked={on}
            class={`pb-goal-row${on ? ' on' : ''}`}
            onClick={() => {
              haptics.lightTick()
              onSelect(g)
            }}
          >
            <span class="pb-goal-ic" aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" focusable="false"><path d={GOAL_PATHS[g]} /></svg>
            </span>
            <span class="pb-goal-text">
              <span class="pb-goal-title">{t(goalKey(g))}</span>
              <span class="pb-goal-desc">{t(goalDescKey(g))}</span>
            </span>
            <span class="pb-goal-radio" aria-hidden="true" />
          </button>
        )
      })}
    </div>
  )
}

function QuickCard({
  a, lang, change,
}: {
  a: PlanAnswers
  lang: Lang
  change: (fn: (a: PlanAnswers) => PlanAnswers) => void
}) {
  const nf = useMemo(() => new Intl.NumberFormat(intlLocale(lang)), [lang])
  const [gearOpen, setGearOpen] = useState(false)

  return (
    <Card>
      <div class="pb-stack">
        <h2 class="pb-label">{t('pb_goal')}</h2>
        <TrainGoalSelector selected={a.goal} onSelect={(g) => change((it) => ({ ...it, goal: g }))} />

        <h2 class="pb-label">{t('pb_days')}</h2>
        <Segmented
          label={t('pb_days')}
          value={String(a.days.size)}
          options={DAY_COUNT_OPTIONS.map((d) => ({ value: String(d), label: nf.format(d) }))}
          onChange={(d) => change((it) => ({ ...it, days: spreadDays(Number(d)), minutesByDay: new Map() }))}
        />

        <h2 class="pb-label">{t('pb_minutes')}</h2>
        <Segmented
          label={t('pb_minutes')}
          value={String(a.minutes)}
          options={MINUTE_OPTIONS.map((m) => ({ value: String(m), label: nf.format(m) }))}
          onChange={(m) => change((it) => ({ ...it, minutes: Number(m), minutesByDay: new Map() }))}
        />

        <h2 class="pb-label">{t('pb_equipment')}</h2>
        <TrainingPlaceSelector equipment={a.equipment} onPick={(_, next) => change((it) => ({ ...it, equipment: next }))} />
        <GearCustomizeRow equipment={a.equipment} onOpen={() => setGearOpen(true)} />
      </div>
      <GearSheet
        open={gearOpen}
        equipment={a.equipment}
        onUpdate={(next) => change((it) => ({ ...it, equipment: next }))}
        onClose={() => setGearOpen(false)}
      />
    </Card>
  )
}

function FitMeSection({
  a, lang, open, editDay, byId, onToggle, onEditDay, onOpenAvoid, change,
}: {
  a: PlanAnswers
  lang: Lang
  open: string
  editDay: number
  byId: ReadonlyMap<string, ExerciseEntity>
  onToggle: (id: string) => void
  onEditDay: (dow: number) => void
  onOpenAvoid: () => void
  change: (fn: (a: PlanAnswers) => PlanAnswers) => void
}) {
  const [expanded, setExpanded] = useState(() => open !== '')
  const sums = rowSummaries(a, t, lang)

  return (
    <div class="pb-fit-wrap">
      <button
        type="button"
        class="pb-fit-toggle"
        aria-expanded={expanded}
        onClick={() => setExpanded((v) => !v)}
      >
        <span class="pb-fit-toggle-ic"><TuneIcon size={18} /></span>
        <span>{t('pb_fit_me')}</span>
        <span class={`pb-fit-toggle-chev${expanded ? ' open' : ''}`}><ExpandMoreIcon size={18} /></span>
      </button>

      {expanded && (
        <>
          <DeepRow title={t('pb_row_you')} summary={sums.you} open={open === 'you'} onToggle={() => onToggle('you')}>
            <YouRow a={a} change={change} />
          </DeepRow>

          <DeepRow title={t('pb_row_body')} summary={sums.body} open={open === 'body'} onToggle={() => onToggle('body')}>
            <BodyRow a={a} change={change} />
          </DeepRow>

          <DeepRow title={t('pb_row_schedule')} summary={sums.schedule} open={open === 'schedule'} onToggle={() => onToggle('schedule')}>
            <ScheduleRow a={a} lang={lang} editDay={editDay} onEditDay={onEditDay} change={change} />
          </DeepRow>

          <DeepRow title={t('pb_row_style')} summary={sums.style} open={open === 'style'} onToggle={() => onToggle('style')}>
            <StyleRow a={a} change={change} />
          </DeepRow>

          <DeepRow title={t('pb_row_avoid')} summary={sums.avoid} open={open === 'avoid'} onToggle={() => onToggle('avoid')}>
            <AvoidRow a={a} byId={byId} onOpenAvoid={onOpenAvoid} change={change} />
          </DeepRow>
        </>
      )}
    </div>
  )
}

function DeepRow({
  title, summary, open, onToggle, children,
}: {
  title: string
  summary: string
  open: boolean
  onToggle: () => void
  children: ComponentChildren
}) {
  return (
    <section class="pb-deep">
      <button type="button" class="pb-deep-head" aria-expanded={open} onClick={onToggle}>
        <span class="pb-deep-title">{title}</span>
        <span class="pb-deep-sum">{summary}</span>
        <span class={`pb-deep-chev${open ? ' open' : ''}`}><ExpandMoreIcon size={20} /></span>
      </button>
      {open && <div class="pb-deep-body">{children}</div>}
    </section>
  )
}

function YouRow({ a, change }: { a: PlanAnswers; change: (fn: (a: PlanAnswers) => PlanAnswers) => void }) {
  return (
    <>
      <Segmented
        label={t('pb_row_you')}
        value={a.experience}
        options={EXPERIENCES.map((e) => ({ value: e, label: t(experienceKey(e)) }))}
        onChange={(e: Experience) => change((it) => ({ ...it, experience: e }))}
      />
      <div class="pb-row">
        <span class="pb-row-text">{t('pb_pushups')}</span>
        <Stepper
          label={t('pb_pushups')}
          value={a.pushUpMax ?? 0}
          step={1}
          min={0}
          max={100}
          format={(v) => (a.pushUpMax != null && v > 0 ? String(v) : '–')}
          onChange={(v) => change((it) => ({ ...it, pushUpMax: v > 0 ? Math.round(v) : null }))}
        />
      </div>
      <div class="pb-row">
        <span class="pb-row-text">{t('pb_pullups')}</span>
        <Stepper
          label={t('pb_pullups')}
          value={a.pullUpMax ?? 0}
          step={1}
          min={0}
          max={100}
          format={(v) => (a.pullUpMax != null && v > 0 ? String(v) : '–')}
          onChange={(v) => change((it) => ({ ...it, pullUpMax: v > 0 ? Math.round(v) : null }))}
        />
      </div>
    </>
  )
}

function BodyRow({ a, change }: { a: PlanAnswers; change: (fn: (a: PlanAnswers) => PlanAnswers) => void }) {
  return (
    <>
      <p class="pb-hint">{t('pb_injury_hint')}</p>
      <div class="pb-chips" role="group" aria-label={t('pb_injury_hint')}>
        {INJURIES.map((inj) => {
          const selected = a.injuries.has(inj)
          return (
            <Chip
              key={inj}
              label={t(injuryKey(inj))}
              selected={selected}
              onClick={() => change((it) => {
                const next = new Set(it.injuries)
                if (selected) next.delete(inj)
                else next.add(inj)
                return { ...it, injuries: next }
              })}
            />
          )
        })}
      </div>

      <p class="pb-hint">{t('pb_focus_hint')}</p>
      <div class="pb-chips" role="group" aria-label={t('pb_focus_hint')}>
        {FOCUS_MUSCLES.map((m) => {
          const selected = a.focus.has(m)
          return (
            <Chip
              key={m}
              label={muscleLabel(m)}
              selected={selected}
              onClick={() => change((it) => {
                const next = new Set(it.focus)
                if (selected) next.delete(m)
                else if (next.size < 3) next.add(m)
                return { ...it, focus: next }
              })}
            />
          )
        })}
      </div>
    </>
  )
}

function ScheduleRow({
  a, lang, editDay, onEditDay, change,
}: {
  a: PlanAnswers
  lang: Lang
  editDay: number
  onEditDay: (dow: number) => void
  change: (fn: (a: PlanAnswers) => PlanAnswers) => void
}) {
  return (
    <>
      <p class="pb-hint">{t('pb_schedule_hint')}</p>
      <div class="pb-days" role="group" aria-label={t('pb_row_schedule')}>
        {[1, 2, 3, 4, 5, 6, 7].map((dow) => {
          const inPlan = a.days.has(dow)
          const editing = editDay === dow && inPlan
          return (
            <button
              key={dow}
              type="button"
              class={`pb-day${inPlan ? ' in-plan' : ''}${editing ? ' editing' : ''}`}
              aria-pressed={inPlan}
              aria-label={dayName(dow, lang)}
              onClick={() => {
                if (!inPlan) {
                  change((it) => ({ ...it, days: new Set([...it.days, dow]) }))
                } else {
                  onEditDay(editDay === dow ? 0 : dow)
                }
              }}
            >
              {dayShort(dow, lang)}
            </button>
          )
        })}
      </div>

      {editDay !== 0 && a.days.has(editDay) && (
        <>
          <div class="pb-row">
            <span class="pb-row-text">{t('pb_day_minutes', dayName(editDay, lang))}</span>
            <Stepper
              label={t('pb_day_minutes', dayName(editDay, lang))}
              value={a.minutesByDay.get(editDay) ?? a.minutes}
              step={5}
              min={10}
              max={120}
              onChange={(v) => change((it) => {
                const next = new Map(it.minutesByDay)
                next.set(editDay, Math.round(v))
                return { ...it, minutesByDay: next }
              })}
            />
          </div>
          <button
            type="button"
            class="pb-day-remove"
            onClick={() => {
              change((it) => {
                const nextDays = new Set(it.days)
                nextDays.delete(editDay)
                const nextMins = new Map(it.minutesByDay)
                nextMins.delete(editDay)
                return { ...it, days: nextDays, minutesByDay: nextMins }
              })
              onEditDay(0)
            }}
          >
            {t('pb_day_remove')}
          </button>
        </>
      )}
    </>
  )
}

function ToggleLine({ title, checked, onChange }: { title: string; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      type="button"
      class="pb-switch-row"
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
    >
      <span class="pb-row-text">{title}</span>
      <span class={`pb-switch${checked ? ' on' : ''}`} aria-hidden="true">
        <span class="pb-switch-knob" />
      </span>
    </button>
  )
}

function StyleRow({ a, change }: { a: PlanAnswers; change: (fn: (a: PlanAnswers) => PlanAnswers) => void }) {
  return (
    <>
      <p class="pb-hint">{t('pb_reps')}</p>
      <Segmented
        label={t('pb_reps')}
        value={a.reps}
        options={REP_PREFS.map((r) => ({ value: r, label: t(repPrefKey(r)) }))}
        onChange={(r: RepPref) => change((it) => ({ ...it, reps: r }))}
      />

      <p class="pb-hint">{t('pb_rest')}</p>
      <Segmented
        label={t('pb_rest')}
        value={a.rest}
        options={REST_PREFS.map((r) => ({ value: r, label: t(restPrefKey(r)) }))}
        onChange={(r: RestPref) => change((it) => ({ ...it, rest: r }))}
      />

      <p class="pb-hint">{t('pb_cardio')}</p>
      <Segmented
        label={t('pb_cardio')}
        value={a.cardio}
        options={CARDIOS.map((c) => ({ value: c, label: t(cardioKey(c)) }))}
        onChange={(c: Cardio) => change((it) => ({ ...it, cardio: c }))}
      />

      <ToggleLine
        title={t('pb_holds')}
        checked={a.holdsOk}
        onChange={(v: boolean) => change((it) => ({ ...it, holdsOk: v }))}
      />
      <ToggleLine
        title={t('pb_warm_up')}
        checked={a.warmUp}
        onChange={(v: boolean) => change((it) => ({ ...it, warmUp: v }))}
      />
      <ToggleLine
        title={t('pb_cool_down')}
        checked={a.coolDown}
        onChange={(v: boolean) => change((it) => ({ ...it, coolDown: v }))}
      />
    </>
  )
}

function AvoidRow({
  a, byId, onOpenAvoid, change,
}: {
  a: PlanAnswers
  byId: ReadonlyMap<string, ExerciseEntity>
  onOpenAvoid: () => void
  change: (fn: (a: PlanAnswers) => PlanAnswers) => void
}) {
  const sorted = [...a.avoidIds].sort()
  return (
    <>
      <p class="pb-hint">{t('pb_avoid_hint')}</p>
      {sorted.length > 0 && (
        <div class="pb-chips">
          {sorted.map((id) => {
            const name = byId.get(id)?.name ?? id
            return (
              <button
                key={id}
                type="button"
                class="pb-avoid-pill"
                aria-label={`${t('cd_remove')}: ${name}`}
                onClick={() => change((it) => {
                  const next = new Set(it.avoidIds)
                  next.delete(id)
                  return { ...it, avoidIds: next }
                })}
              >
                <span>{name}</span>
                <CloseIcon size={14} />
              </button>
            )
          })}
        </div>
      )}
      <div>
        <Button variant="tonal" block icon={<AddIcon size={18} />} onClick={onOpenAvoid}>
          {t('pb_avoid_add')}
        </Button>
      </div>
    </>
  )
}

function ReviewSection({
  review, byId, lang, busy, onSwap, onUse, onAnother, onChange,
}: {
  review: BuiltPlan
  byId: ReadonlyMap<string, ExerciseEntity>
  lang: Lang
  busy: boolean
  onSwap: (dayOfWeek: number, index: number) => void
  onUse: () => void
  onAnother: () => void
  onChange: () => void
}) {
  const days = useMemo(
    () => [...review.draft.days].sort((a, b) => a.dayOfWeek - b.dayOfWeek),
    [review],
  )

  return (
    <div class="pb-review">
      {days.length === 0 ? (
        <Card>
          <p class="pb-hint">{t('pb_empty')}</p>
        </Card>
      ) : (
        days.map((day) => (
          <ReviewDay
            key={day.dayOfWeek}
            day={day}
            review={review}
            byId={byId}
            lang={lang}
            onSwap={onSwap}
          />
        ))
      )}

      <Button
        block
        class="pb-cta"
        icon={<CheckIcon size={20} />}
        disabled={days.length === 0 || busy}
        loading={busy}
        onClick={onUse}
      >
        {t('pb_use')}
      </Button>

      <div class="pb-review-actions">
        <Button variant="tonal" block icon={<RefreshIcon size={18} />} onClick={onAnother}>
          {t('pb_another')}
        </Button>
        <Button variant="tonal" block onClick={onChange}>
          {t('pb_change')}
        </Button>
      </div>
    </div>
  )
}

function ReviewDay({
  day, review, byId, lang, onSwap,
}: {
  day: PlanDraftDay
  review: BuiltPlan
  byId: ReadonlyMap<string, ExerciseEntity>
  lang: Lang
  onSwap: (dayOfWeek: number, index: number) => void
}) {
  const focus = review.days.get(day.dayOfWeek)
  const why = review.why.get(day.dayOfWeek) ?? []
  const mins = PlanBuilder.estimateMinutes(day)

  return (
    <Card>
      <div class="pb-stack">
        <div class="pb-rday-head">
          <div class="pb-rday-titles">
            <h2 class="pb-rday-name">{dayName(day.dayOfWeek, lang)}</h2>
            <span class="pb-rday-focus">{focus != null ? t(dayFocusKey(focus)) : day.title}</span>
          </div>
          <span class="pb-rday-mins">{t('pb_minutes_total', mins)}</span>
        </div>

        {why.length > 0 && (
          <p class="pb-rday-why">{why.map((w) => t(whyTagKey(w))).join(' ')}</p>
        )}

        <div class="pb-rday-items">
          {day.items.map((item, idx) => {
            const name = byId.get(item.exerciseId)?.name ?? item.name ?? item.exerciseId
            return (
              <div key={`${item.exerciseId}-${idx}`} class="pb-ritem">
                <span class="pb-ritem-text">{`${name} · ${scheme(item)}`}</span>
                <IconButton label={t('pb_swap', name)} onClick={() => onSwap(day.dayOfWeek, idx)}>
                  <SwapIcon size={18} />
                </IconButton>
              </div>
            )
          })}
        </div>
      </div>
    </Card>
  )
}
