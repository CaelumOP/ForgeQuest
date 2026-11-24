import { audit, fromActivePlan } from '../../domain/routine-auditor'
import { AuditCard } from '../plan/AuditCard'
import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { getLang, plural, t, useLang, intlLocale } from '../../i18n'
import type { ExerciseRow, PlanDayRow, PlanItemRow, PlanRow } from '../../data/db'
import { useProgress } from '../../data/progress'
import { configuredEquipment, updateSettings, weightUnit } from '../../data/settings'
import {
  activatePlan, addCustomExercise, addPlanItem, applyPlan, deletePlan, deletePlanItem, importSharedPlan,
  movePlanItem, restorePlanItem, setPairWithNext, setPlanScheduleMode, swapPlanDays, swapPlanItem,
  updatePlanDay, updatePlanItem, useAllPlans, useLibrary, type PlanDayWithItems,
} from '../../data/workouts'
import { localizePlan, STARTER_PLANS, type PlanDraft } from '../../data/seed/starter-plans'
import { ALL_ACTIVE_EQUIPMENT, adaptPlanToEquipment, expandLegacyEquipment, type Equipment } from '../../domain/exercise-swap-engine'
import { dayOfWeek, todayEpochDay, WEEK_ORDER } from '../../domain/dates'
import type { ExerciseKind, Muscle } from '../../domain/muscle'
import {
  canPairWithNext, exportSharedPlan, isPairedWithNext, isPairedWithPrev, parseAndValidateSharedPlan,
  weeklyMuscleVolume, type PlanParseResult,
} from '../../domain/plan-share'
import { PROGRESSION_MODES, parseProgressionMode, type ProgressionMode } from '../../domain/progression'
import { isExtraBlock } from '../../domain/pr-picker'
import { dueDay, parseScheduleMode, type ScheduleMode } from '../../domain/rotation'
import { fromKg, loadStep, toKg, unitLabel, type WeightUnit } from '../../domain/units'
import { Button, Card, CoachMark, Dialog, EmptyState, Segmented, Sheet, Stepper, TextField, haptics, reducedMotion, toast } from '../kit'
import { PlanEquipmentRow } from '../profile/GearSheet'
import planPrompt from './plan-prompt.txt?raw'
import { ExercisePicker } from './ExercisePicker'
import { CopyIcon, DeleteIcon, DocIcon, DownIcon, DragIcon, EditIcon, InfoIcon, ListIcon, HistoryIcon, SparkleIcon, SwapIcon, UpIcon, AddIcon } from './glyphs'
import { blockLabel, dayName, dayShort, isRestTitle, muscleLabel, prescription } from './helpers'
import { stepReorder } from './reorder'
import { PastePlanSheet } from '../coach/PastePlanSheet'
import '../plan/plan41.css'
import './workouts.css'

const cx = (...a: (string | false | undefined)[]) => a.filter(Boolean).join(' ')

type Undo = { text: string; run: () => void | Promise<void> }
type Panel = null | 'change' | 'templates' | 'previous' | 'move' | 'rename' | 'picker' | 'paste' | 'share' | 'import'

export function PlanEditorScreen({ param }: { param?: string }) {
  const lang = useLang()
  const s = useProgress()
  const library = useLibrary()
  const allPlans = useAllPlans()
  const openTemplates = param === 'templates'
  const dayParam = param?.startsWith('day-') ? Number(param.slice(4)) : NaN
  const [selected, setSelected] = useState(() => (dayParam >= 1 && dayParam <= 7 ? dayParam : dayOfWeek(todayEpochDay())))
  const [panel, setPanel] = useState<Panel>(() => (openTemplates ? 'templates' : null))
  useEffect(() => { if (openTemplates) setPanel('templates') }, [openTemplates])
  const [editing, setEditing] = useState<PlanItemRow | null>(null)
  const [swapping, setSwapping] = useState<PlanItemRow | null>(null)
  const [pending, setPending] = useState<PlanDraft | null>(null)
  const pendingSource = useRef('starter')
  const [sharedPending, setSharedPending] = useState<Extract<PlanParseResult, { ok: true }> | null>(null)
  const [undo, setUndo] = useState<Undo | null>(null)
  const timer = useRef<number>(0)
  useEffect(() => () => clearTimeout(timer.current), [])
  // Drag a row by its handle to reorder; the Move up / Move down buttons in the item editor stay.
  const [drag, setDrag] = useState<{ id: number; offset: number; order: number[]; start: number } | null>(null)
  const rowEls = useRef(new Map<number, HTMLElement>())
  const lastY = useRef(0)

  const findings = useMemo(() => s?.plan
    ? audit(fromActivePlan(s.plan), s.exercises, parseScheduleMode(s.plan.plan.scheduleMode))
    : [], [s?.plan, s?.exercises])

  if (!s) return <div class="content wk" />
  const plan = s.plan
  const day = plan?.days.find((d) => d.day.dayOfWeek === selected)
  const unit = weightUnit(s.settings)
  const equipment = configuredEquipment(s.settings)
  const arabic = lang === 'ar'
  const viewed = param && !openTemplates ? allPlans.find((p) => String(p.id) === param) : undefined

  const showUndo = (u: Undo) => {
    setUndo(u)
    clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setUndo(null), 6000)
  }
  const remove = async (item: PlanItemRow, name: string) => {
    await deletePlanItem(item.id!)
    showUndo({ text: t('food_removed', name), run: () => restorePlanItem(item) })
  }
  const setRest = (d: PlanDayRow, rest: boolean) =>
    updatePlanDay({ ...d, isRest: rest, title: rest ? t('today_rest_day') : isRestTitle(d.title) ? t('plan_workout') : d.title })
  const createAndAdd = async (name: string, kind: ExerciseKind, muscle: Muscle, dayId: number) => {
    await addPlanItem(dayId, await addCustomExercise(name, kind, [muscle], null))
  }
  const createAndSwap = async (name: string, kind: ExerciseKind, muscle: Muscle, item: PlanItemRow) => {
    await swapPlanItem(item, await addCustomExercise(name, kind, [muscle], null))
  }
  const applyPending = async () => {
    if (!pending) return
    const draft = pending
    setPending(null)
    await applyPlan(draft, pendingSource.current)
    await updateSettings((cur) => ({ ...cur, weeklyTargetOverride: 0 }))
    toast(t('plan_switched_to', draft.name))
  }

  const scheduleMode = parseScheduleMode(plan?.plan.scheduleMode)
  const nextRotation = plan && scheduleMode === 'ROTATION' ? dueDay(plan, selected) : null
  const orderedItems = day && !day.day.isRest
    ? (drag ? drag.order.map((id) => day.items.find((i) => i.id === id)).filter((i): i is PlanItemRow => !!i) : day.items)
    : []
  const editingIdx = editing && day ? day.items.findIndex((i) => i.id === editing.id) : -1

  return (
    <div class="content wk">
      <div class="wk-head">
        <div class="wk-head-text">
          <h1 class="wk-title">{t('plan_your_plan')}</h1>
          <div class="wk-sub">{plan?.plan.name ?? t('plan_no_plan')}</div>
        </div>
        {plan && (
          <button type="button" class="kit-iconbtn" aria-label={t('plan41_share_plan')} onClick={() => setPanel('share')}>
            <ShareIcon />
          </button>
        )}
      </div>

      {viewed && !viewed.active && (
        <Card>
          <div class="wk-live">
            <div class="wk-grow"><div class="wk-t">{t('workouts_web_not_current', viewed.name)}</div></div>
            <Button onClick={async () => { await activatePlan(viewed.id!); await updateSettings((cur) => ({ ...cur, weeklyTargetOverride: 0 })); toast(t('plan_switched', viewed.name)); location.hash = '#/plan' }}>{t('workouts_web_activate')}</Button>
          </div>
        </Card>
      )}

      <CoachMark id="coach_plan" title={t('coach_plan_t')} body={t('coach_plan_b')} below>
        <Button variant="tonal" block icon={<SwapIcon />} onClick={() => setPanel('change')}>{t('plan_change')}</Button>
      </CoachMark>

      {plan && (
        <Card>
          <div class="p41-card-stack">
            <div class="p41-card-head">
              <div class="p41-card-title">{t('plan41_schedule_mode')}</div>
              <div class="p41-card-sub">
                {t(scheduleMode === 'ROTATION' ? 'plan41_schedule_rotation_hint' : 'plan41_schedule_weekday_hint')}
              </div>
              {nextRotation && (
                <div class="p41-next-note">{t('plan41_rotation_next', nextRotation.day.title)}</div>
              )}
            </div>
            <Segmented
              label={t('plan41_schedule_mode')}
              value={scheduleMode}
              options={[
                { value: 'WEEKDAY', label: t('plan41_mode_weekday') },
                { value: 'ROTATION', label: t('plan41_mode_rotation') },
              ]}
              onChange={(m: ScheduleMode) => { if (plan.plan.id != null) void setPlanScheduleMode(plan.plan.id, m) }}
            />
          </div>
        </Card>
      )}

      <AuditCard findings={findings} />

      {!plan ? (
        <Card>
          <EmptyState
            icon={<EditIcon size={32} />} title={t('today_no_plan')} body={t('pb_entry_info')}
            action={<div class="wk-empty-btn"><Button onClick={() => { location.hash = '#/plan-builder' }}>{t('action_build_plan')}</Button></div>}
          />
        </Card>
      ) : (
        <div class="wk-strip" role="tablist" aria-label={t('workouts_web_days')}>
          {WEEK_ORDER.map((dow) => (
            <button key={dow} type="button" role="tab" class="wk-day" aria-selected={dow === selected} aria-label={dayName(dow, lang)} onClick={() => setSelected(dow)}>
              <span>{dayShort(dow, lang)}</span>
            </button>
          ))}
        </div>
      )}

      {day && plan && (
        <Card>
          <div class="wk-day-head">
            <div class="wk-grow">
              <button type="button" class="wk-linkbtn small" onClick={() => setPanel('move')}>
                {dayName(selected, lang)}<SwapIcon size={16} /><span class="kit-sr">{t('cd_move_day')}</span>
              </button>
              {day.day.isRest
                ? <span class="wk-t">{isRestTitle(day.day.title) ? t('today_rest_day') : day.day.title}</span>
                : <button type="button" class="wk-linkbtn title" onClick={() => setPanel('rename')}>{isRestTitle(day.day.title) ? t('today_rest_day') : day.day.title}</button>}
            </div>
            <label class="wk-s" for="wk-rest">{t('today_rest_day')}</label>
            <input id="wk-rest" type="checkbox" role="switch" class="wk-switch blue" checked={day.day.isRest} onChange={(e) => setRest(day.day, (e.target as HTMLInputElement).checked)} />
          </div>
          {!day.day.isRest && orderedItems.map((item) => {
            const ex = s.exercises.get(item.exerciseId)
            const name = ex?.name ?? item.exerciseId
            const pMode = parseProgressionMode(item.progressionMode)
            const sub = [
              item.block.trim() ? blockLabel(item.block) : null,
              item.circuit ? plural('plan_circuit_rounds', item.sets) : null,
              prescription(item, ex?.kind, unit, arabic),
              t('plan_rest_seconds', item.restSeconds),
            ].filter(Boolean).join(' · ')
            return (
              <div
                class={cx('wk-item', drag?.id === item.id && 'wk-item-drag')} key={item.id}
                ref={(el) => { if (el) rowEls.current.set(item.id!, el); else rowEls.current.delete(item.id!) }}
                // Lifted: 3 % larger (not under reduced motion, where a teal outline marks it instead).
                style={drag && drag.id === item.id ? { transform: `translateY(${drag.offset}px)${reducedMotion() ? '' : ' scale(1.03)'}` } : undefined}
              >
                <button
                  type="button" class="kit-iconbtn wk-ibtn wk-handle" aria-label={`${t('cd_drag_reorder')}: ${name}`}
                  onPointerDown={(e) => {
                    e.preventDefault()
                    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
                    lastY.current = e.clientY
                    const order = day.items.map((i) => i.id!)
                    setDrag({ id: item.id!, offset: 0, order, start: order.indexOf(item.id!) })
                    haptics.heavyClick()
                  }}
                  onPointerMove={(e) => {
                    if (!drag || drag.id !== item.id) return
                    const step = stepReorder(drag.order, drag.id, drag.offset + e.clientY - lastY.current, (id) => rowEls.current.get(id)?.offsetHeight ?? 56)
                    lastY.current = e.clientY
                    if (step.swapped) haptics.lightTick()
                    setDrag({ ...drag, order: step.order, offset: step.offset })
                  }}
                  onPointerUp={() => {
                    if (!drag || drag.id !== item.id) return
                    const delta = drag.order.indexOf(drag.id) - drag.start
                    setDrag(null)
                    haptics.tick()
                    if (delta !== 0) void movePlanItem(item, delta)
                  }}
                  onPointerCancel={() => setDrag(null)}
                  onKeyDown={(e) => {
                    if (e.key === 'ArrowUp') { e.preventDefault(); void movePlanItem(item, -1) }
                    if (e.key === 'ArrowDown') { e.preventDefault(); void movePlanItem(item, 1) }
                  }}
                ><DragIcon /></button>
                <button type="button" class="wk-item-main" onClick={() => setEditing(item)}>
                  <span class="wk-item-name" style={{ whiteSpace: 'normal' }}>{name}</span>
                  <span class="wk-s">{sub}</span>
                  {(item.supersetGroup != null || item.autoWarmup || pMode !== 'DOUBLE') && (
                    <span class="p41-meta-row">
                      {item.supersetGroup != null && <span class="p41-meta-chip">{t('plan41_superset_tag')}</span>}
                      {item.autoWarmup && <span class="p41-meta-chip">{t('plan41_auto_warmup_tag')}</span>}
                      {pMode !== 'DOUBLE' && (
                        <span class="p41-meta-chip">
                          {t(pMode === 'LINEAR' ? 'plan41_prog_linear' : 'plan41_prog_maintain')}
                        </span>
                      )}
                    </span>
                  )}
                </button>
                <button type="button" class="kit-iconbtn wk-ibtn teal" aria-label={`${t('live_swap_exercise')}: ${name}`} onClick={() => setSwapping(item)}><SwapIcon /></button>
                <button type="button" class="kit-iconbtn wk-ibtn" aria-label={`${t('cd_remove')}: ${name}`} onClick={() => remove(item, name)}><DeleteIcon /></button>
              </div>
            )
          })}
          <div style={{ marginBlockStart: '8px' }}>
            <Button variant="tonal" block icon={<AddIcon />} onClick={() => setPanel('picker')}>{t('live_add_exercise')}</Button>
          </div>
        </Card>
      )}

      <PlanEquipmentRow equipment={equipment} onSave={(encoded) => updateSettings((cur) => ({ ...cur, equipment: encoded }))} />

      <ExercisePicker
        open={panel === 'picker' && !!day} title={t('plan_add_to_day', dayName(selected, lang))} library={library}
        userEquipment={equipment} onClose={() => setPanel(null)}
        onPick={(ex) => { if (day) addPlanItem(day.day.id!, ex.id); setPanel(null) }}
        onCreate={(n, k, m) => { if (day) createAndAdd(n, k, m, day.day.id!); setPanel(null) }}
      />
      <ExercisePicker
        open={!!swapping} title={t('live_swap_exercise')} library={library} userEquipment={equipment}
        sourceExercise={swapping ? s.exercises.get(swapping.exerciseId) : undefined}
        initialMuscle={swapping ? s.exercises.get(swapping.exerciseId)?.primary[0] : undefined}
        onClose={() => setSwapping(null)}
        onPick={(ex) => { if (swapping) swapPlanItem(swapping, ex.id); setSwapping(null) }}
        onCreate={(n, k, m) => { if (swapping) createAndSwap(n, k, m, swapping); setSwapping(null) }}
      />

      {editing && day && (
        <ItemEditor
          key={editing.id}
          item={editing}
          exercise={s.exercises.get(editing.exerciseId)}
          prevItem={editingIdx > 0 ? day.items[editingIdx - 1] : undefined}
          prevName={editingIdx > 0 ? (s.exercises.get(day.items[editingIdx - 1].exerciseId)?.name ?? day.items[editingIdx - 1].exerciseId) : ''}
          nextItem={editingIdx >= 0 && editingIdx < day.items.length - 1 ? day.items[editingIdx + 1] : undefined}
          nextName={editingIdx >= 0 && editingIdx < day.items.length - 1 ? (s.exercises.get(day.items[editingIdx + 1].exerciseId)?.name ?? day.items[editingIdx + 1].exerciseId) : ''}
          pairedInitially={isPairedWithNext(day.items, editingIdx)}
          pairedWithPrev={isPairedWithPrev(day.items, editingIdx)}
          unit={unit}
          canMoveUp={editingIdx > 0}
          canMoveDown={editingIdx >= 0 && editingIdx < day.items.length - 1}
          onMove={async (draft, delta) => { await updatePlanItem(draft); await movePlanItem(draft, delta); setEditing(null) }}
          onSwap={async (draft) => { await updatePlanItem(draft); setSwapping(draft); setEditing(null) }}
          onSave={async (it, pairNext) => {
            await updatePlanItem(it)
            if (day.day.id != null && it.id != null) await setPairWithNext(day.day.id, it.id, pairNext)
            setEditing(null)
          }}
          onClose={() => setEditing(null)}
        />
      )}

      <ChangePlanSheet
        open={panel === 'change'} onClose={() => setPanel(null)} hasPrevious={allPlans.length > 1} hasPlan={!!plan}
        onBuilder={() => { setPanel(null); location.replace('#/plan-builder') }}
        onTemplates={() => setPanel('templates')} onPrevious={() => setPanel('previous')} onPaste={() => setPanel('paste')}
        onShare={() => setPanel('share')} onImport={() => setPanel('import')}
      />
      <PastePlanSheet open={panel === 'paste'} onClose={() => setPanel(null)} onParsed={(draft) => { pendingSource.current = 'manual'; setPanel(null); setPending(draft) }} />
      <TemplatesSheet
        open={panel === 'templates'} onClose={() => setPanel(null)} exercises={s.exercises}
        onPick={(draft) => { pendingSource.current = 'starter'; setPanel(null); setPending(localizePlan(draft, getLang())) }}
      />
      {panel === 'share' && plan && (
        <SharePlanSheet
          planName={plan.plan.name}
          json={exportSharedPlan(plan, s.exercises)}
          onClose={() => setPanel(null)}
        />
      )}
      {panel === 'import' && (
        <ImportPlanSheet
          exercises={s.exercises}
          onClose={() => setPanel(null)}
          onPreview={(res) => { setPanel(null); setSharedPending(res) }}
        />
      )}
      {sharedPending && (
        <SharedPlanPreviewSheet
          parsed={sharedPending}
          exercises={s.exercises}
          onClose={() => setSharedPending(null)}
          onImport={async () => {
            const p = sharedPending
            setSharedPending(null)
            await importSharedPlan(p.plan)
            await updateSettings((cur) => ({ ...cur, weeklyTargetOverride: 0 }))
            toast(t('plan_switched_to', p.plan.name))
          }}
        />
      )}
      <PreviousSheet
        open={panel === 'previous'} onClose={() => setPanel(null)} plans={allPlans}
        onActivate={async (p) => { setPanel(null); await activatePlan(p.id!); await updateSettings((cur) => ({ ...cur, weeklyTargetOverride: 0 })); toast(t('plan_switched', p.name)) }}
        onDelete={async (p) => { await deletePlan(p.id!); toast(t('lib_deleted', p.name)) }}
      />
      {panel === 'move' && day && plan && (
        <MoveDaySheet
          selected={selected} days={plan.days} lang={lang} onClose={() => setPanel(null)}
          onPick={async (dow) => {
            await swapPlanDays(plan.plan.id!, selected, dow)
            toast(t('plan_days_swapped', dayName(selected, lang), dayName(dow, lang)))
            setSelected(dow); setPanel(null)
          }}
        />
      )}
      {panel === 'rename' && day && (
        <RenameSheet
          title={day.day.title} onClose={() => setPanel(null)}
          onSave={(name) => { updatePlanDay({ ...day.day, title: name.trim() || t('plan_workout') }); setPanel(null) }}
        />
      )}
      <PlanPreviewSheet
        draft={pending} exercises={s.exercises} userEquipment={equipment} onClose={() => setPending(null)}
        onAdapt={(d) => setPending(d)} onApply={applyPending}
      />

      {undo && (
        <div class="wk-undo" role="status">
          <span>{undo.text}</span>
          <Button variant="text" onClick={() => { const u = undo; setUndo(null); clearTimeout(timer.current); u.run() }}>{t('action_undo')}</Button>
        </div>
      )}
    </div>
  )
}

// ---------------------------------------------------------------- Item editor

function ItemEditor({
  item, exercise, prevName, nextItem, nextName, pairedInitially, pairedWithPrev, unit,
  canMoveUp, canMoveDown, onMove, onSwap, onSave, onClose,
}: {
  item: PlanItemRow; exercise: ExerciseRow | undefined
  prevItem?: PlanItemRow; prevName: string
  nextItem?: PlanItemRow; nextName: string
  pairedInitially: boolean; pairedWithPrev: boolean
  unit: WeightUnit; canMoveUp: boolean; canMoveDown: boolean
  onMove: (draft: PlanItemRow, delta: number) => void; onSwap: (draft: PlanItemRow) => void
  onSave: (i: PlanItemRow, pairNext: boolean) => void; onClose: () => void
}) {
  const arabic = getLang() === 'ar'
  const [d, setD] = useState(item)
  const [pairNext, setPairNext] = useState(pairedInitially)
  const kind = exercise?.kind ?? 'REPS'
  const sec = t('unit_s')
  const minReps = d.repsMin ?? 10
  const maxReps = d.repsMax ?? minReps
  const wUnit = unitLabel(unit, arabic)
  const isExtra = isExtraBlock(d.block)
  const pMode = parseProgressionMode(d.progressionMode)
  const showPair = !!nextItem && !isExtra && !isExtraBlock(nextItem.block)
  const setsMatch = !!nextItem && d.sets === nextItem.sets
  const canTogglePair = showPair && !pairedWithPrev && canPairWithNext([d, nextItem!], 0)
  const pairHint = pairedWithPrev
    ? t('plan41_pair_hint_prev', prevName)
    : !setsMatch && nextItem
      ? t('plan41_pair_hint_sets', nextName, nextItem.sets)
      : pairNext && canTogglePair
        ? t('plan41_pair_hint_active', nextName)
        : t('plan41_pair_hint_ready', nextName)
  const progHint = pMode === 'LINEAR' ? t('plan41_prog_linear_hint')
    : pMode === 'MAINTAIN' ? t('plan41_prog_maintain_hint')
      : t('plan41_prog_double_hint')

  return (
    <Sheet open onClose={onClose} title={exercise?.name ?? item.exerciseId}>
      <Button variant="tonal" icon={<SwapIcon />} onClick={() => onSwap(d)}>{t('live_swap_exercise')}</Button>
      <Row label={d.circuit ? t('plan_rounds') : t('live_sets')}>
        <Stepper
          label={d.circuit ? t('plan_rounds') : t('live_sets')} value={d.sets} min={1} max={10}
          onChange={(v) => {
            setD({ ...d, sets: v })
            if (nextItem && v !== nextItem.sets) setPairNext(false)
          }}
        />
      </Row>
      {kind === 'HOLD' ? (
        <Row label={t('kind_hold')}>
          <Stepper label={t('kind_hold')} value={d.seconds ?? 30} min={5} max={3600} step={5} format={(n) => `${n} ${sec}`} onChange={(v) => setD({ ...d, seconds: v })} />
        </Row>
      ) : (
        <>
          <Row label={t('plan_reps_min')}>
            <Stepper label={t('plan_reps_min')} value={minReps} min={1} max={999} onChange={(v) => setD({ ...d, repsMin: v, repsMax: d.repsMax == null ? null : Math.max(d.repsMax, v) })} />
          </Row>
          <Row label={t('plan_reps_max')}>
            <Stepper label={t('plan_reps_max')} value={maxReps} min={minReps} max={999} onChange={(v) => setD({ ...d, repsMax: Math.max(v, minReps) })} />
          </Row>
        </>
      )}
      {kind !== 'WEIGHTED' && (
        <div class="wk-setting">
          <div class="wk-grow">
            <label for="wk-addw" class="wk-lbl">{t('plan_add_weight')}</label>
            <span class="wk-s">{t('plan_add_weight_hint')}</span>
          </div>
          <input id="wk-addw" type="checkbox" role="switch" class="wk-switch" checked={d.weightKg != null} onChange={(e) => setD({ ...d, weightKg: (e.target as HTMLInputElement).checked ? 0 : null })} />
        </div>
      )}
      {(kind === 'WEIGHTED' || d.weightKg != null) && (
        <Row label={t('stepper_weight')}>
          <Stepper
            label={t('stepper_weight')} value={Math.round(fromKg(unit, d.weightKg ?? 0) * 10) / 10} min={0} max={Math.round(fromKg(unit, 999))} step={loadStep(unit)}
            format={(n) => `${n.toFixed(1)} ${wUnit}`} onChange={(v) => setD({ ...d, weightKg: toKg(unit, v) })}
          />
        </Row>
      )}
      <Row label={t('live_rest')}>
        <Stepper label={t('live_rest')} value={d.restSeconds} min={0} max={900} step={15} format={(n) => `${n} ${sec}`} onChange={(v) => setD({ ...d, restSeconds: v })} />
      </Row>
      {!isExtra && (
        <div class="p41-card-stack">
          <div class="p41-card-head">
            <span class="wk-lbl">{t('plan41_prog_mode')}</span>
            <span class="wk-s">{progHint}</span>
          </div>
          <Segmented
            label={t('plan41_prog_mode')}
            value={pMode}
            options={PROGRESSION_MODES.map((m) => ({
              value: m,
              label: t(m === 'LINEAR' ? 'plan41_prog_linear' : m === 'MAINTAIN' ? 'plan41_prog_maintain' : 'plan41_prog_double'),
            }))}
            onChange={(m: ProgressionMode) => setD({ ...d, progressionMode: m })}
          />
        </div>
      )}
      {!isExtra && kind === 'WEIGHTED' && (
        <div class="wk-setting">
          <div class="wk-grow">
            <label for="wk-warmup" class="wk-lbl">{t('plan41_auto_warmup')}</label>
            <span class="wk-s">{t('plan41_auto_warmup_hint')}</span>
          </div>
          <input id="wk-warmup" type="checkbox" role="switch" class="wk-switch" checked={Boolean(d.autoWarmup)} onChange={(e) => setD({ ...d, autoWarmup: (e.target as HTMLInputElement).checked })} />
        </div>
      )}
      {showPair && (
        <div class={cx('wk-setting', !canTogglePair && 'p41-switch-disabled')}>
          <div class="wk-grow">
            <label for="wk-pair" class="wk-lbl">{t('plan41_pair_next')}</label>
            <span class="wk-s">{pairHint}</span>
          </div>
          <input id="wk-pair" type="checkbox" role="switch" class="wk-switch" disabled={!canTogglePair} checked={pairNext && canTogglePair} onChange={(e) => setPairNext((e.target as HTMLInputElement).checked)} />
        </div>
      )}
      <div class="wk-setting">
        <div class="wk-grow">
          <label for="wk-circ" class="wk-lbl">{t('plan_part_circuit')}</label>
          <span class="wk-s">{t('plan_circuit_hint', d.block)}</span>
        </div>
        <input id="wk-circ" type="checkbox" role="switch" class="wk-switch" checked={d.circuit} onChange={(e) => setD({ ...d, circuit: (e.target as HTMLInputElement).checked })} />
      </div>
      <div class="wk-edit-row">
        <span>{t('plan_order')}</span>
        <Button variant="tonal" icon={<UpIcon size={18} />} disabled={!canMoveUp} onClick={() => onMove(d, -1)}>{t('plan_move_up')}</Button>
        <Button variant="tonal" icon={<DownIcon size={18} />} disabled={!canMoveDown} onClick={() => onMove(d, 1)}>{t('plan_move_down')}</Button>
      </div>
      <Button block onClick={() => onSave(d, pairNext && canTogglePair)}>{t('action_save')}</Button>
    </Sheet>
  )
}

const Row = ({ label, children }: { label: string; children: preact.ComponentChildren }) => (
  <div class="wk-edit-row"><span>{label}</span>{children}</div>
)

// ---------------------------------------------------------------- Change plan and friends

function ChoiceRow({ icon, title, info, onClick, extra }: { icon: preact.ComponentChildren; title: string; info: string; onClick?: () => void; extra?: preact.ComponentChildren }) {
  const [show, setShow] = useState(false)
  const body = <><span class="wk-choice-ic">{icon}</span><span class="wk-choice-t">{title}</span></>
  return (
    <div>
      <div class="wk-choice">
        {onClick ? <button type="button" class="wk-choice-main" onClick={onClick}>{body}</button> : <div class="wk-choice-main">{body}</div>}
        <button type="button" class="kit-iconbtn" style={{ color: show ? 'var(--teal)' : 'var(--text-2)' }} aria-expanded={show} aria-label={t('cd_what_is_this', title)} onClick={() => setShow(!show)}>
          <InfoIcon />
        </button>
      </div>
      {show && <p class="wk-info">{info}</p>}
      {extra && <div class="wk-info-extra">{extra}</div>}
    </div>
  )
}

const TuneIcon = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false">
    <path d="M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z" />
  </svg>
)

const ShareIcon = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false">
    <path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z" />
  </svg>
)

function ChangePlanSheet({ open, onClose, hasPrevious, hasPlan, onBuilder, onTemplates, onPrevious, onPaste, onShare, onImport }: {
  open: boolean; onClose: () => void; hasPrevious: boolean; hasPlan: boolean
  onBuilder: () => void; onTemplates: () => void; onPrevious: () => void; onPaste: () => void; onShare: () => void; onImport: () => void
}) {
  const copy = async () => {
    try { await navigator.clipboard.writeText(planPrompt); toast(t('plan_prompt_copied')) } catch { toast(t('workouts_web_copy_failed')) }
  }
  return (
    <Sheet open={open} onClose={onClose} title={t('plan_change')}>
      <ChoiceRow icon={<SparkleIcon />} title={t('plan_gemini')} info={t('plan_gemini_info')} extra={<><Button variant="tonal" icon={<CopyIcon size={18} />} onClick={copy}>{t('plan_gemini_copy')}</Button> <Button variant="tonal" icon={<DocIcon size={18} />} onClick={onPaste}>{t('plan_gemini_paste')}</Button></>} />
      <ChoiceRow icon={<TuneIcon />} title={t('pb_title')} info={t('pb_entry_info')} onClick={onBuilder} />
      <ChoiceRow icon={<ListIcon />} title={t('plan_ready_made')} info={t('plan_ready_made_info')} onClick={onTemplates} />
      <ChoiceRow icon={<DocIcon />} title={t('plan41_import_plan')} info={t('plan41_import_plan_info')} onClick={onImport} />
      {hasPlan && <ChoiceRow icon={<ShareIcon />} title={t('plan41_share_plan')} info={t('plan41_share_plan_info')} onClick={onShare} />}
      {hasPrevious && <ChoiceRow icon={<HistoryIcon />} title={t('plan_previous')} info={t('plan_previous_info')} onClick={onPrevious} />}
    </Sheet>
  )
}

function TemplatesSheet({ open, onClose, exercises, onPick }: {
  open: boolean; onClose: () => void; exercises: ReadonlyMap<string, ExerciseRow>; onPick: (p: PlanDraft) => void
}) {
  const lang = useLang()
  return (
    <Sheet open={open} onClose={onClose} title={t('plan_ready_made_title')}>
      {STARTER_PLANS.map((p) => {
        const l = localizePlan(p, lang)
        const trainDays = l.days.filter((d) => !d.isRest && d.items.length > 0).length
        const vol = weeklyMuscleVolume(l, exercises)
        return (
          <button key={p.name} type="button" class="wk-opt" onClick={() => onPick(p)}>
            <span class="wk-t">{l.name}</span>
            <span class="wk-s">{`${plural('plan41_days_per_week', trainDays, trainDays)} · ${l.description}`}</span>
            {vol.length > 0 && (
              <span class="p41-chips" style={{ marginBlockStart: '6px' }}>
                {vol.slice(0, 5).map((v) => (
                  <span key={v.muscle} class="p41-data-chip">{t('plan41_muscle_sets_item', muscleLabel(v.muscle), v.sets)}</span>
                ))}
              </span>
            )}
          </button>
        )
      })}
    </Sheet>
  )
}

function PreviousSheet({ open, onClose, plans, onActivate, onDelete }: { open: boolean; onClose: () => void; plans: PlanRow[]; onActivate: (p: PlanRow) => void; onDelete: (p: PlanRow) => void }) {
  const [del, setDel] = useState<PlanRow | null>(null)
  const src = (p: PlanRow) => t(p.source === 'coach' ? 'plan_src_coach' : p.source === 'docs' ? 'plan_src_docs' : p.source === 'starter' ? 'plan_src_starter' : 'plan_src_you')
  return (
    <>
      <Sheet open={open} onClose={onClose} title={t('plan_previous')}>
        <p class="wk-s">{t('plan_previous_tap')}</p>
        {plans.map((p) => (
          <div class="wk-opt-row" key={p.id}>
            <button type="button" class={`wk-opt${p.active ? ' on' : ''}`} disabled={p.active} onClick={() => onActivate(p)}>
              <span class="wk-t">{p.name}</span>
              <span class="wk-s">{[src(p), new Date(p.createdAt).toLocaleDateString(intlLocale(), { dateStyle: 'medium' }), p.active ? t('plan_current') : null].filter(Boolean).join(' · ')}</span>
            </button>
            {!p.active && <button type="button" class="kit-iconbtn wk-ibtn" aria-label={`${t('cd_delete_plan')}: ${p.name}`} onClick={() => setDel(p)}><DeleteIcon /></button>}
          </div>
        ))}
      </Sheet>
      <Dialog
        open={!!del} destructive title={t('food_delete_title', del?.name ?? '')} message={t('plan_delete_body')} confirmLabel={t('action_delete')}
        onCancel={() => setDel(null)} onConfirm={() => { const p = del!; setDel(null); onDelete(p) }}
      />
    </>
  )
}

function MoveDaySheet({ selected, days, lang, onClose, onPick }: { selected: number; days: PlanDayWithItems[]; lang: string; onClose: () => void; onPick: (dow: number) => void }) {
  return (
    <Sheet open onClose={onClose} title={t('plan_move_title', dayName(selected, lang))}>
      <p class="wk-s">{t('plan_move_body')}</p>
      {WEEK_ORDER.filter((d) => d !== selected).map((dow) => {
        const target = days.find((x) => x.day.dayOfWeek === dow)
        return (
          <button key={dow} type="button" class="wk-prow" onClick={() => onPick(dow)}>
            <span class="wk-prow-name" style={{ flex: 1 }}>{dayName(dow, lang)}</span>
            <span class="wk-prow-sub">{!target || target.day.isRest ? t('live_rest') : target.day.title}</span>
          </button>
        )
      })}
    </Sheet>
  )
}

function RenameSheet({ title, onClose, onSave }: { title: string; onClose: () => void; onSave: (v: string) => void }) {
  const [v, setV] = useState(title)
  return (
    <Sheet open onClose={onClose} title={t('plan_day_name')}>
      <TextField label={t('plan_day_name')} value={v} onInput={(x) => setV(x.slice(0, 40))} />
      <div class="wk-pair">
        <Button variant="tonal" onClick={onClose}>{t('action_cancel')}</Button>
        <Button onClick={() => onSave(v)}>{t('action_save')}</Button>
      </div>
    </Sheet>
  )
}

// ---------------------------------------------------------------- Preview

/** Shows a whole proposed week before it replaces the current plan. Also used by the Coach screen for coach-made plans. */
export function PlanPreviewSheet({ draft, exercises, userEquipment, onApply, onClose, onAdapt }: {
  draft: PlanDraft | null; exercises: Map<string, ExerciseRow>; userEquipment?: ReadonlySet<Equipment>
  onApply: () => void; onClose: () => void; onAdapt?: (adapted: PlanDraft) => void
}) {
  const lang = useLang()
  if (!draft) return null
  const eq = expandLegacyEquipment(userEquipment ?? new Set<Equipment>())
  const limited = eq.size > 0 && !ALL_ACTIVE_EQUIPMENT.every((e) => eq.has(e))
  const [adapted, count] = limited && onAdapt ? adaptPlanToEquipment(draft, exercises, eq) : [draft, 0]
  const vol = weeklyMuscleVolume(draft, exercises)
  return (
    <Sheet open onClose={onClose} title={draft.name}>
      {draft.description.trim() && <p class="wk-s">{draft.description}</p>}
      {vol.length > 0 && (
        <div>
          <div class="p41-vol-label">{t('plan41_weekly_volume')}</div>
          <div class="p41-chips">
            {vol.slice(0, 8).map((v) => (
              <span key={v.muscle} class="p41-data-chip">{t('plan41_muscle_sets_item', muscleLabel(v.muscle), v.sets)}</span>
            ))}
          </div>
        </div>
      )}
      <div>
        {[...draft.days].sort((a, b) => WEEK_ORDER.indexOf(a.dayOfWeek) - WEEK_ORDER.indexOf(b.dayOfWeek)).map((d) => {
          const rest = d.isRest || d.items.length === 0
          return (
            <div class="wk-preview-day" key={d.dayOfWeek}>
              <span class="wk-preview-dow">{dayShort(d.dayOfWeek, lang)}</span>
              <div class="wk-grow">
                <div class="wk-t" style={{ fontWeight: 400 }}>{rest ? t('live_rest') : d.title}</div>
                {!rest && <div class="wk-s">{d.items.map((i) => exercises.get(i.exerciseId)?.name ?? i.name ?? i.exerciseId).join(t('list_separator'))}</div>}
              </div>
            </div>
          )
        })}
      </div>
      {count > 0 && onAdapt && <Button variant="tonal" block icon={<SwapIcon />} onClick={() => onAdapt(adapted)}>{plural('plan_adapt_button', count, count)}</Button>}
      <p class="wk-s">{t('plan_replaces')}</p>
      <Button block onClick={onApply}>{t('plan_use_this')}</Button>
    </Sheet>
  )
}

function SharePlanSheet({ planName, json, onClose }: { planName: string; json: string; onClose: () => void }) {
  const copy = async () => {
    try { await navigator.clipboard.writeText(json); toast(t('plan41_copied')) } catch { toast(t('workouts_web_copy_failed')) }
  }
  const share = async () => {
    if (navigator.share) {
      try { await navigator.share({ title: planName, text: json }); return } catch { /* user cancelled or unsupported */ }
    }
    await copy()
  }
  return (
    <Sheet open onClose={onClose} title={t('plan41_share_plan')}>
      <p class="wk-s">{t('plan41_share_plan_info')}</p>
      <div class="p41-json-box">{json}</div>
      <div class="p41-actions-row">
        <Button variant="tonal" icon={<CopyIcon size={18} />} onClick={copy}>{t('plan41_copy_json')}</Button>
        <Button icon={<ShareIcon />} onClick={share}>{t('plan41_send_share')}</Button>
      </div>
    </Sheet>
  )
}

function ImportPlanSheet({ exercises, onClose, onPreview }: {
  exercises: ReadonlyMap<string, ExerciseRow>
  onClose: () => void
  onPreview: (res: Extract<PlanParseResult, { ok: true }>) => void
}) {
  const [raw, setRaw] = useState('')
  const [err, setErr] = useState<string | null>(null)
  const paste = async () => {
    try {
      const clip = await navigator.clipboard.readText()
      if (clip) { setRaw(clip); setErr(null) }
    } catch { /* clipboard read denied */ }
  }
  const preview = () => {
    const res = parseAndValidateSharedPlan(raw, exercises)
    if (res.ok) onPreview(res)
    else setErr(t('plan41_import_error', res.errors[0] ?? ''))
  }
  return (
    <Sheet open onClose={onClose} title={t('plan41_import_plan')}>
      <p class="p41-safe-note">{t('plan41_import_safe_note')}</p>
      <TextField
        label={t('plan41_import_plan')}
        multiline
        value={raw}
        placeholder={t('plan41_import_hint')}
        error={err ?? undefined}
        onInput={(v) => { setRaw(v); setErr(null) }}
      />
      {!raw.trim() && (
        <Button variant="tonal" onClick={paste}>{t('action_paste')}</Button>
      )}
      <Button block disabled={!raw.trim()} onClick={preview}>{t('plan41_import_preview')}</Button>
    </Sheet>
  )
}

function SharedPlanPreviewSheet({ parsed, exercises, onClose, onImport }: {
  parsed: Extract<PlanParseResult, { ok: true }>
  exercises: ReadonlyMap<string, ExerciseRow>
  onClose: () => void
  onImport: () => void
}) {
  const lang = useLang()
  const { plan, customExercises, volume } = parsed
  return (
    <Sheet open onClose={onClose} title={plan.name}>
      {plan.description.trim() && <p class="wk-s">{plan.description}</p>}
      {customExercises.length > 0 && (
        <p class="wk-s" style={{ color: 'var(--gold)' }}>
          {t('plan41_import_custom_note', customExercises.length, customExercises.join(t('list_separator')))}
        </p>
      )}
      {volume.length > 0 && (
        <div>
          <div class="p41-vol-label">{t('plan41_weekly_volume')}</div>
          <div class="p41-chips">
            {volume.slice(0, 8).map((v) => (
              <span key={v.muscle} class="p41-data-chip">{t('plan41_muscle_sets_item', muscleLabel(v.muscle), v.sets)}</span>
            ))}
          </div>
        </div>
      )}
      <div>
        {[...plan.days].sort((a, b) => WEEK_ORDER.indexOf(a.dayOfWeek) - WEEK_ORDER.indexOf(b.dayOfWeek)).map((d) => {
          const rest = d.isRest || d.items.length === 0
          return (
            <div class="wk-preview-day" key={d.dayOfWeek}>
              <span class="wk-preview-dow">{dayShort(d.dayOfWeek, lang)}</span>
              <div class="wk-grow">
                <div class="wk-t" style={{ fontWeight: 400 }}>{rest ? t('live_rest') : d.title}</div>
                {!rest && (
                  <div class="wk-s">
                    {d.items.map((i) => exercises.get(i.exerciseId)?.name ?? i.name ?? i.exerciseId).join(t('list_separator'))}
                  </div>
                )}
              </div>
            </div>
          )
        })}
      </div>
      <p class="p41-safe-note">{t('plan41_import_safe_note')}</p>
      <Button block onClick={onImport}>{t('plan41_import_confirm')}</Button>
    </Sheet>
  )
}
