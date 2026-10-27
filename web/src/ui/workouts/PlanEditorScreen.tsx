import { useEffect, useRef, useState } from 'preact/hooks'
import { getLang, plural, t, useLang } from '../../i18n'
import type { ExerciseRow, PlanDayRow, PlanItemRow, PlanRow } from '../../data/db'
import { useProgress } from '../../data/progress'
import { configuredEquipment, weightUnit } from '../../data/settings'
import {
  activatePlan, addCustomExercise, addPlanItem, applyPlan, deletePlan, deletePlanItem, movePlanItem, restorePlanItem,
  swapPlanDays, swapPlanItem, updatePlanDay, updatePlanItem, useAllPlans, useLibrary, type PlanDayWithItems,
} from '../../data/workouts'
import { localizePlan, STARTER_PLANS, type PlanDraft } from '../../data/seed/starter-plans'
import { adaptPlanToEquipment, EQUIPMENT, type Equipment } from '../../domain/exercise-swap-engine'
import { dayOfWeek, todayEpochDay } from '../../domain/dates'
import type { ExerciseKind, Muscle } from '../../domain/muscle'
import { fromKg, loadStep, toKg, unitLabel, type WeightUnit } from '../../domain/units'
import { Button, Card, Dialog, Sheet, Stepper, TextField, toast } from '../kit'
import planPrompt from './plan-prompt.txt?raw'
import { ExercisePicker } from './ExercisePicker'
import { CopyIcon, DeleteIcon, DocIcon, DownIcon, DragIcon, InfoIcon, ListIcon, HistoryIcon, SparkleIcon, SwapIcon, UpIcon, AddIcon } from './glyphs'
import { blockLabel, dayName, isRestTitle, prescription } from './helpers'
import { stepReorder } from './reorder'
import { buzz } from '../live/feedback'
import { PastePlanSheet } from '../coach/PastePlanSheet'
import './workouts.css'

const cx = (...a: (string | false | undefined)[]) => a.filter(Boolean).join(' ')

type Undo = { text: string; run: () => void | Promise<void> }
type Panel = null | 'change' | 'templates' | 'previous' | 'move' | 'rename' | 'picker' | 'paste'

export function PlanEditorScreen({ param }: { param?: string }) {
  const lang = useLang()
  const s = useProgress()
  const library = useLibrary()
  const allPlans = useAllPlans()
  const [selected, setSelected] = useState(() => dayOfWeek(todayEpochDay()))
  const [panel, setPanel] = useState<Panel>(null)
  const [editing, setEditing] = useState<PlanItemRow | null>(null)
  const [swapping, setSwapping] = useState<PlanItemRow | null>(null)
  const [pending, setPending] = useState<PlanDraft | null>(null)
  const pendingSource = useRef('starter')
  const [undo, setUndo] = useState<Undo | null>(null)
  const timer = useRef<number>(0)
  useEffect(() => () => clearTimeout(timer.current), [])
  // Drag a row by its handle to reorder; the Move up / Move down buttons in the item editor stay.
  const [drag, setDrag] = useState<{ id: number; offset: number; order: number[]; start: number } | null>(null)
  const rowEls = useRef(new Map<number, HTMLElement>())
  const lastY = useRef(0)

  if (!s) return <div class="content wk" />
  const plan = s.plan
  const day = plan?.days.find((d) => d.day.dayOfWeek === selected)
  const unit = weightUnit(s.settings)
  const equipment = configuredEquipment(s.settings)
  const arabic = lang === 'ar'
  const viewed = param ? allPlans.find((p) => String(p.id) === param) : undefined

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
    toast(t('plan_switched_to', draft.name))
  }

  return (
    <div class="content wk">
      <div>
        <h1 class="wk-title">{t('plan_your_plan')}</h1>
        <div class="wk-sub">{plan?.plan.name ?? t('plan_no_plan')}</div>
      </div>

      {viewed && !viewed.active && (
        <Card>
          <div class="wk-live">
            <div class="wk-grow"><div class="wk-t">{t('workouts_web_not_current', viewed.name)}</div></div>
            <Button onClick={async () => { await activatePlan(viewed.id!); toast(t('plan_switched', viewed.name)); location.hash = '#/plan' }}>{t('workouts_web_activate')}</Button>
          </div>
        </Card>
      )}

      <Button variant="tonal" block icon={<SwapIcon />} onClick={() => setPanel('change')}>{t('plan_change')}</Button>

      <div class="wk-strip" role="tablist" aria-label={t('workouts_web_days')}>
        {[1, 2, 3, 4, 5, 6, 7].map((dow) => (
          <button key={dow} type="button" role="tab" class="wk-day" aria-selected={dow === selected} aria-label={dayName(dow, lang)} onClick={() => setSelected(dow)}>
            <span>{dayName(dow, lang, 'short')}</span>
          </button>
        ))}
      </div>

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
          {!day.day.isRest && (drag ? drag.order.map((id) => day.items.find((i) => i.id === id)).filter((i): i is PlanItemRow => !!i) : day.items).map((item) => {
            const ex = s.exercises.get(item.exerciseId)
            const name = ex?.name ?? item.exerciseId
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
                style={drag && drag.id === item.id ? { transform: `translateY(${drag.offset}px)` } : undefined}
              >
                <button
                  type="button" class="kit-iconbtn wk-ibtn wk-handle" aria-label={`${t('cd_drag_reorder')}: ${name}`}
                  onPointerDown={(e) => {
                    e.preventDefault()
                    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
                    lastY.current = e.clientY
                    const order = day.items.map((i) => i.id!)
                    setDrag({ id: item.id!, offset: 0, order, start: order.indexOf(item.id!) })
                    buzz(8)
                  }}
                  onPointerMove={(e) => {
                    if (!drag || drag.id !== item.id) return
                    const step = stepReorder(drag.order, drag.id, drag.offset + e.clientY - lastY.current, (id) => rowEls.current.get(id)?.offsetHeight ?? 56)
                    lastY.current = e.clientY
                    if (step.swapped) buzz(8)
                    setDrag({ ...drag, order: step.order, offset: step.offset })
                  }}
                  onPointerUp={() => {
                    if (!drag || drag.id !== item.id) return
                    const delta = drag.order.indexOf(drag.id) - drag.start
                    setDrag(null)
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

      {editing && (
        <ItemEditor
          key={editing.id} item={editing} exercise={s.exercises.get(editing.exerciseId)} unit={unit}
          canMoveUp={!!day && day.items.findIndex((i) => i.id === editing.id) > 0}
          canMoveDown={!!day && day.items.findIndex((i) => i.id === editing.id) < day.items.length - 1 && day.items.some((i) => i.id === editing.id)}
          onMove={(delta) => { movePlanItem(editing, delta); setEditing(null) }}
          onSwap={() => { setSwapping(editing); setEditing(null) }}
          onSave={(it) => { updatePlanItem(it); setEditing(null) }}
          onClose={() => setEditing(null)}
        />
      )}

      <ChangePlanSheet
        open={panel === 'change'} onClose={() => setPanel(null)} hasPrevious={allPlans.length > 1}
        onTemplates={() => setPanel('templates')} onPrevious={() => setPanel('previous')} onPaste={() => setPanel('paste')}
      />
      <PastePlanSheet open={panel === 'paste'} onClose={() => setPanel(null)} onParsed={(draft) => { pendingSource.current = 'manual'; setPanel(null); setPending(draft) }} />
      <TemplatesSheet
        open={panel === 'templates'} onClose={() => setPanel(null)}
        onPick={(draft) => { pendingSource.current = 'starter'; setPanel(null); setPending(localizePlan(draft, getLang())) }}
      />
      <PreviousSheet
        open={panel === 'previous'} onClose={() => setPanel(null)} plans={allPlans}
        onActivate={async (p) => { setPanel(null); await activatePlan(p.id!); toast(t('plan_switched', p.name)) }}
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

function ItemEditor({ item, exercise, unit, canMoveUp, canMoveDown, onMove, onSwap, onSave, onClose }: {
  item: PlanItemRow; exercise: ExerciseRow | undefined; unit: WeightUnit; canMoveUp: boolean; canMoveDown: boolean
  onMove: (delta: number) => void; onSwap: () => void; onSave: (i: PlanItemRow) => void; onClose: () => void
}) {
  const arabic = getLang() === 'ar'
  const [d, setD] = useState(item)
  const kind = exercise?.kind ?? 'REPS'
  const sec = t('unit_s')
  const minReps = d.repsMin ?? 10
  const maxReps = d.repsMax ?? minReps
  const wUnit = unitLabel(unit, arabic)
  return (
    <Sheet open onClose={onClose} title={exercise?.name ?? item.exerciseId}>
      <Button variant="tonal" icon={<SwapIcon />} onClick={onSwap}>{t('live_swap_exercise')}</Button>
      <Row label={d.circuit ? t('plan_rounds') : t('live_sets')}>
        <Stepper label={d.circuit ? t('plan_rounds') : t('live_sets')} value={d.sets} min={1} max={10} onChange={(v) => setD({ ...d, sets: v })} />
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
      <div class="wk-setting">
        <div class="wk-grow">
          <label for="wk-circ" class="wk-lbl">{t('plan_part_circuit')}</label>
          <span class="wk-s">{t('plan_circuit_hint', d.block)}</span>
        </div>
        <input id="wk-circ" type="checkbox" role="switch" class="wk-switch" checked={d.circuit} onChange={(e) => setD({ ...d, circuit: (e.target as HTMLInputElement).checked })} />
      </div>
      <div class="wk-edit-row">
        <span>{t('plan_order')}</span>
        <Button variant="tonal" icon={<UpIcon size={18} />} disabled={!canMoveUp} onClick={() => onMove(-1)}>{t('plan_move_up')}</Button>
        <Button variant="tonal" icon={<DownIcon size={18} />} disabled={!canMoveDown} onClick={() => onMove(1)}>{t('plan_move_down')}</Button>
      </div>
      <Button block onClick={() => onSave(d)}>{t('action_save')}</Button>
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

function ChangePlanSheet({ open, onClose, hasPrevious, onTemplates, onPrevious, onPaste }: { open: boolean; onClose: () => void; hasPrevious: boolean; onTemplates: () => void; onPrevious: () => void; onPaste: () => void }) {
  const copy = async () => {
    try { await navigator.clipboard.writeText(planPrompt); toast(t('plan_prompt_copied')) } catch { toast(t('workouts_web_copy_failed')) }
  }
  return (
    <Sheet open={open} onClose={onClose} title={t('plan_change')}>
      <ChoiceRow icon={<SparkleIcon />} title={t('plan_gemini')} info={t('plan_gemini_info')} extra={<><Button variant="tonal" icon={<CopyIcon size={18} />} onClick={copy}>{t('plan_gemini_copy')}</Button> <Button variant="tonal" icon={<DocIcon size={18} />} onClick={onPaste}>{t('plan_gemini_paste')}</Button></>} />
      <ChoiceRow icon={<ListIcon />} title={t('plan_ready_made')} info={t('plan_ready_made_info')} onClick={onTemplates} />
      {hasPrevious && <ChoiceRow icon={<HistoryIcon />} title={t('plan_previous')} info={t('plan_previous_info')} onClick={onPrevious} />}
      <ChoiceRow icon={<DocIcon />} title={t('plan_import_docs')} info={t('plan_docs_info')} onClick={() => { location.hash = '#/profile' }} />
    </Sheet>
  )
}

function TemplatesSheet({ open, onClose, onPick }: { open: boolean; onClose: () => void; onPick: (p: PlanDraft) => void }) {
  const lang = useLang()
  return (
    <Sheet open={open} onClose={onClose} title={t('plan_ready_made_title')}>
      {STARTER_PLANS.map((p) => {
        const l = localizePlan(p, lang)
        return (
          <button key={p.name} type="button" class="wk-opt" onClick={() => onPick(p)}>
            <span class="wk-t">{l.name}</span>
            <span class="wk-s">{l.description}</span>
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
              <span class="wk-s">{[src(p), new Date(p.createdAt).toLocaleDateString(getLang(), { dateStyle: 'medium' }), p.active ? t('plan_current') : null].filter(Boolean).join(' · ')}</span>
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
      {[1, 2, 3, 4, 5, 6, 7].filter((d) => d !== selected).map((dow) => {
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
  const eq = userEquipment ?? new Set<Equipment>()
  const limited = eq.size > 0 && eq.size < EQUIPMENT.length
  const [adapted, count] = limited && onAdapt ? adaptPlanToEquipment(draft, exercises, eq) : [draft, 0]
  return (
    <Sheet open onClose={onClose} title={draft.name}>
      {draft.description.trim() && <p class="wk-s">{draft.description}</p>}
      <div>
        {[...draft.days].sort((a, b) => a.dayOfWeek - b.dayOfWeek).map((d) => {
          const rest = d.isRest || d.items.length === 0
          return (
            <div class="wk-preview-day" key={d.dayOfWeek}>
              <span class="wk-preview-dow">{dayName(d.dayOfWeek, lang, 'short')}</span>
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
