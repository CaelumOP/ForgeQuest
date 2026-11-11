// Port of app/src/main/java/com/forgequest/app/ui/plan/PlanBuilderScreen.kt.
import type { ComponentChildren } from 'preact'
import { useEffect, useMemo, useState } from 'preact/hooks'
import { intlLocale, t, useLang, type Lang } from '../../i18n'
import { loadSettings, updateSettings } from '../../data/settings'
import { applyPlan, useLibrary } from '../../data/workouts'
import type { ExerciseEntity, PlanDraftDay } from '../../domain/entities'
import {
  EQUIPMENT, EQUIPMENT_LABEL, EQUIPMENT_PRESETS, presetEquipment, type Equipment,
} from '../../domain/exercise-swap-engine'
import {
  CARDIOS, EXPERIENCES, INJURIES, PlanBuilder, REP_PREFS, REST_PREFS, TRAIN_GOALS,
  defaultPlanAnswers, encodePlanAnswers, planAnswersToInput, spreadDays,
  type BuiltPlan, type Cardio, type Experience, type PlanAnswers, type RepPref, type RestPref,
} from '../../domain/plan-builder'
import { AddIcon, CheckIcon, CloseIcon, ExpandMoreIcon } from '../icons'
import { Button, Card, Chip, IconButton, Segmented, Stepper, toast } from '../kit'
import { goBack } from '../nav'
import { ExercisePicker } from '../workouts/ExercisePicker'
import { SwapIcon } from '../workouts/glyphs'
import { dayName, dayShort, muscleLabel } from '../workouts/helpers'
import {
  DAY_COUNT_OPTIONS, FOCUS_MUSCLES, MINUTE_OPTIONS,
  activeEquipmentPreset, cardioKey, dayFocusKey, equipmentSetting, experienceKey, goalKey,
  initialAnswers, injuryKey, localizeBuiltPlan, presetShortLabel, repPrefKey, restPrefKey,
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
        <h1 class="pb-title">{t(review == null ? 'pb_title' : 'pb_review_title')}</h1>
        <p class="pb-sub">{t(review == null ? 'pb_quick_hint' : 'pb_review_hint')}</p>
        {review == null && (
          <button
            type="button"
            class="pb-ready-link"
            onClick={() => location.replace('#/plan/templates')}
          >
            {t('plan_ready_made_link')}
          </button>
        )}
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

function QuickCard({
  a, lang, change,
}: {
  a: PlanAnswers
  lang: Lang
  change: (fn: (a: PlanAnswers) => PlanAnswers) => void
}) {
  const nf = useMemo(() => new Intl.NumberFormat(intlLocale(lang)), [lang])
  const activePreset = activeEquipmentPreset(a.equipment)

  return (
    <Card>
      <div class="pb-stack">
        <h2 class="pb-label">{t('pb_goal')}</h2>
        <div class="pb-chips" role="group" aria-label={t('pb_goal')}>
          {TRAIN_GOALS.map((g) => (
            <Chip
              key={g}
              label={t(goalKey(g))}
              selected={a.goal === g}
              onClick={() => change((it) => ({ ...it, goal: g }))}
            />
          ))}
        </div>

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
        <div class="pb-chips" role="group" aria-label={t('pb_equipment')}>
          {EQUIPMENT_PRESETS.map((preset) => (
            <Chip
              key={preset}
              label={presetShortLabel(preset, lang)}
              selected={activePreset === preset}
              onClick={() => change((it) => ({ ...it, equipment: presetEquipment(preset) }))}
            />
          ))}
        </div>
      </div>
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

          <DeepRow title={t('pb_row_gear')} summary={sums.gear} open={open === 'gear'} onToggle={() => onToggle('gear')}>
            <GearRow a={a} lang={lang} change={change} />
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

function GearRow({
  a, lang, change,
}: {
  a: PlanAnswers
  lang: Lang
  change: (fn: (a: PlanAnswers) => PlanAnswers) => void
}) {
  const ar = lang === 'ar'
  return (
    <div class="pb-chips" role="group" aria-label={t('pb_row_gear')}>
      {EQUIPMENT.filter((e) => e !== 'BODYWEIGHT').map((eq: Equipment) => {
        const selected = a.equipment.has(eq)
        return (
          <Chip
            key={eq}
            label={EQUIPMENT_LABEL[eq][ar ? 'ar' : 'en']}
            selected={selected}
            onClick={() => change((it) => {
              const next = new Set(it.equipment)
              if (selected) {
                next.delete(eq)
              } else {
                next.add(eq)
                next.add('BODYWEIGHT')
              }
              return { ...it, equipment: next }
            })}
          />
        )
      })}
    </div>
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
