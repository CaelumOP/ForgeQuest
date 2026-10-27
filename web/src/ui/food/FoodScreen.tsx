import { useEffect, useRef, useState } from 'preact/hooks'
import { getLang, plural, t, intlLocale } from '../../i18n'
import { dayOfWeek, todayEpochDay } from '../../domain/dates'
import { formatDay } from '../day-format'
import type { FoodCategory, FoodPart, FoodRow, MealRow } from '../../data/db'
import { categoryForNow, deleteFood, deleteMeal, logFoods, logName, logOnce, placesOf, restoreMeal, saveFood, totalsOf, useFoodUsage, useFoods, useMealsOn, useRecentOneOffs, withParts } from '../../data/food'
import { score } from '../../domain/food-search'
import { scaledKcal } from '../../domain/nutrition'
import { evaluateBadges, useProgress } from '../../data/progress'
import { useSettings } from '../../data/settings'
import { AddIcon, BackChevron, CameraIcon, CheckIcon, CloseIcon, EditIcon, ForwardChevron, PinIcon, RemoveIcon, RestaurantIcon, SearchIcon } from '../icons'
import { Button, Card, Chip, Dialog, IconButton, NumberField, Ring, Sheet, SwipeRow, TextField, toast } from '../kit'
import { fmtQty, lastSevenDays, qtyMinus, qtyPlus } from './helpers'
import { dayShort } from '../workouts/helpers'
import { PartsSheet, PlaceField, partsCount } from './FoodParts'
import './food.css'

const CATS: FoodCategory[] = ['BREAKFAST', 'LUNCH', 'DINNER', 'SNACKS', 'DRINKS']
/** " · Badge unlocked: X" when logging just earned one, else "". */
export async function badgeSuffix(): Promise<string> {
  try {
    const b = (await evaluateBadges())[0]
    if (!b) return ''
    const key = `badge_${b.id}_title`
    const v = t(key)
    return t('badge_unlocked_suffix', v === key ? b.title : v)
  } catch { return '' }
}
export const catLabel = (c: FoodCategory): string => t(`food_cat_${c.toLowerCase()}`)
const fmtInt = (n: number) => Math.round(n).toLocaleString(intlLocale())
const timeOf = (ms: number) => new Intl.DateTimeFormat(intlLocale(), { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(ms))
const blankFood = (category: FoodCategory): FoodRow => ({ name: '', category, kcal: 0, protein: 0, serving: '', carbs: 0, photoPath: null, pinned: false, createdAt: Date.now() })

function CategoryTabs({ value, onChange, dots, label }: { value: FoodCategory; onChange: (c: FoodCategory) => void; dots?: Set<FoodCategory>; label: string }) {
  return (
    <div class="food-tabs" role="tablist" aria-label={label}>
      {CATS.map((c) => (
        <button key={c} type="button" role="tab" class="food-tab" aria-selected={c === value} onClick={() => onChange(c)}>
          {catLabel(c)}{dots?.has(c) && <span class="food-tab-dot" aria-hidden="true" />}
        </button>
      ))}
    </div>
  )
}

function Switch({ on, onChange, label }: { on: boolean; onChange: (v: boolean) => void; label: string }) {
  return <button type="button" class="food-switch" role="switch" aria-checked={on} aria-label={label} onClick={() => onChange(!on)} />
}

function DayPicker({ day, today, onShift, onToday }: { day: number; today: number; onShift: (d: number) => void; onToday: () => void }) {
  const isToday = day === today
  const label = isToday ? t('day_today') : day === today - 1 ? t('day_yesterday') : formatDay(day, { weekday: 'long', month: 'short', day: 'numeric' })
  return (
    <div class="food-day" role="group" aria-label={t('food_w_day_nav')}>
      <IconButton label={t('cd_previous_day')} onClick={() => onShift(-1)}><span class="mirror"><BackChevron /></span></IconButton>
      <div class="food-day-mid">
        <span class="food-day-label" aria-live="polite">{label}</span>
        {!isToday && <button type="button" class="food-day-back" onClick={onToday}>{t('food_back_to_today')}</button>}
      </div>
      <IconButton label={t('cd_next_day')} disabled={isToday} onClick={() => onShift(1)}><ForwardChevron /></IconButton>
    </div>
  )
}

function Totals({ meals, kcalGoal, proteinGoal, carbsGoal, trackCarbs }: { meals: MealRow[]; kcalGoal: number; proteinGoal: number; carbsGoal: number; trackCarbs: boolean }) {
  const tot = totalsOf(meals)
  const over = tot.kcal > kcalGoal
  const ring = (value: number, goal: number, color: string, track: string, label: string, num: string, sub: string) => (
    <div class="food-ring">
      <Ring value={goal > 0 ? value / goal : 0} color={color} track={track} size={76} stroke={8} label={label}>
        <span class="food-ring-num" style={{ color: `var(${color})` }}>{num}</span>
      </Ring>
      <span class="food-ring-lbl">{label}</span>
      <span class="food-ring-goal">{sub}</span>
    </div>
  )
  return (
    <Card>
      <div class="food-rings">
        {ring(tot.protein, proteinGoal, '--teal', '--teal-track', t('label_protein'), fmtInt(tot.protein), t('food_protein_of_goal', fmtInt(tot.protein), fmtInt(proteinGoal)))}
        {ring(tot.kcal, kcalGoal, over ? '--gold' : '--blue', over ? '--gold-track' : '--blue-track', over ? t('food_calories_surplus') : t('food_w_ring_kcal'), fmtInt(tot.kcal), `${fmtInt(tot.kcal)} / ${fmtInt(kcalGoal)}`)}
        {trackCarbs && ring(tot.carbs, carbsGoal, '--purple', '--purple-track', t('label_carbs'), fmtInt(tot.carbs), t('food_carbs_of_goal', fmtInt(tot.carbs), fmtInt(carbsGoal)))}
      </div>
    </Card>
  )
}

function WeekCard({ proteinGoal, trackCarbs }: { proteinGoal: number; trackCarbs: boolean }) {
  const snap = useProgress()
  const today = todayEpochDay()
  const days = lastSevenDays(today)
  const byDay = new Map((snap?.nutritionByDay ?? []).map((n) => [n.epochDay, n]))
  const logged = days.map((d) => byDay.get(d)).filter((n) => n != null)
  if (!logged.length) return null
  const avg = (f: (n: (typeof logged)[number]) => number) => logged.reduce((a, n) => a + f(n), 0) / logged.length
  const hits = logged.filter((n) => n.protein >= proteinGoal).length
  const summary = plural('food_week_summary', logged.length, fmtInt(avg((n) => n.kcal)), fmtInt(avg((n) => n.protein)), hits, logged.length)
    + (trackCarbs ? t('food_week_carbs', fmtInt(avg((n) => n.carbs))) : '')
  return (
    <Card>
      <h3 class="food-week-title">{t('food_last_7_days')}</h3>
      <p class="food-week-sub">{summary}</p>
      <div class="food-week-bars">
        {days.map((d) => {
          const n = byDay.get(d)
          const ratio = Math.min(1, Math.max(0, (n?.protein ?? 0) / Math.max(1, proteinGoal)))
          const letter = dayShort(dayOfWeek(d), getLang())
          return (
            <div key={d} class={`food-week-col${d === today ? ' today' : ''}`} role="img" aria-label={t('food_w_week_bar', formatDay(d, { weekday: 'long' }), fmtInt(n?.protein ?? 0))}>
              <div class="food-week-bar">
                {n && ratio > 0 && <div class={`food-week-fill${n.protein >= proteinGoal ? '' : ' low'}`} style={{ blockSize: `${Math.max(3, 32 * ratio)}px` }} />}
              </div>
              <span aria-hidden="true">{letter}</span>
            </div>
          )
        })}
      </div>
    </Card>
  )
}

function FoodItem({ food, qty, onToggle, onQty, onEdit, onParts }: { food: FoodRow; qty: number | undefined; onToggle: () => void; onQty: (q: number) => void; onEdit: () => void; onParts?: () => void }) {
  const on = qty != null
  const sub = [t('food_kcal', food.kcal), t('food_g_protein', Math.round(food.protein)), food.serving || null].filter(Boolean).join(' · ')
  return (
    <div class={`food-row${on ? ' on' : ''}`}>
      <div class="food-row-body">
        <button type="button" class="food-row-main" aria-pressed={on} onClick={onToggle}>
          <span class="food-check">{on && <CheckIcon size={18} />}</span>
          <span class="food-row-text">
            <span class="food-row-name">{food.pinned && <PinIcon size={14} />}<span>{food.name}</span></span>
            {food.place && <span class="food-row-place">{food.place}</span>}
            <span class="food-row-sub">{sub}</span>
          </span>
        </button>
        {onParts && food.parts && <button type="button" class="food-row-parts" onClick={onParts}>{partsCount(food.parts)} · {t('food_parts_change')}</button>}
      </div>
      {on && (
        <div class="food-qty" role="group" aria-label={t('food_w_qty', food.name)}>
          <IconButton label={t('food_w_less')} tone="tonal" onClick={() => onQty(qtyMinus(qty))}><RemoveIcon /></IconButton>
          <output aria-live="polite">{fmtQty(qty)}</output>
          <IconButton label={t('food_w_more')} tone="tonal" onClick={() => onQty(qtyPlus(qty))}><AddIcon /></IconButton>
        </div>
      )}
      <IconButton label={t('food_w_edit', food.name)} onClick={onEdit}><EditIcon size={20} /></IconButton>
    </div>
  )
}

function MealItem({ meal, onDelete }: { meal: MealRow; onDelete: () => void }) {
  const q = meal.quantity !== 1 ? `${fmtQty(meal.quantity)} ` : ''
  return (
    <div class="food-meal">
      <div class="food-meal-text">
        <span class="food-meal-name">{q}{meal.name}</span>
        <span class="food-meal-sub">{timeOf(meal.loggedAt)} · {catLabel(meal.category)}</span>
      </div>
      <div class="food-meal-nums">
        <span class="food-kcal">{t('food_kcal', meal.kcal)}</span>
        <span class="food-prot">{t('food_g', Math.round(meal.protein))}</span>
      </div>
      <IconButton label={`${t('cd_remove')} ${meal.name}`} onClick={onDelete}><CloseIcon size={18} /></IconButton>
    </div>
  )
}

const numText = (n: number) => (n === 0 ? null : n)

function QuickLogSheet({ open, initial, carbsOn, onLog, onClose }: { open: boolean; initial: FoodCategory; carbsOn: boolean; onLog: (n: string, c: FoodCategory, k: number, p: number, cb: number) => void; onClose: () => void }) {
  const recent = useRecentOneOffs()
  const [name, setName] = useState('')
  const [kcal, setKcal] = useState<number | null>(null)
  const [protein, setProtein] = useState<number | null>(null)
  const [carbs, setCarbs] = useState<number | null>(null)
  const [cat, setCat] = useState(initial)
  useEffect(() => { if (open) { setName(''); setKcal(null); setProtein(null); setCarbs(null); setCat(initial) } }, [open])
  return (
    <Sheet open={open} onClose={onClose} title={t('food_quick_log')}>
      <p class="food-note">{t('food_quick_log_hint')}</p>
      {recent.length > 0 && (
        <>
          <span class="food-note">{t('food_recent')}</span>
          <div class="food-recent">
            {recent.map((m) => (
              <Chip key={m.id} label={`${m.name} · ${m.kcal}`} selected={m.name === name} onClick={() => { setName(m.name); setKcal(m.kcal); setProtein(numText(m.protein)); setCarbs(numText(m.carbs)) }} />
            ))}
          </div>
        </>
      )}
      <TextField label={t('food_what_was_it')} value={name} onInput={(v) => setName(v.slice(0, 48))} />
      <div class="food-two">
        <NumberField label={t('home_legend_calories')} value={kcal} onChange={setKcal} min={0} max={99999} />
        <NumberField label={t('home_legend_protein')} value={protein} onChange={setProtein} min={0} max={9999} decimal />
      </div>
      {carbsOn && <NumberField label={t('label_carbs_g')} value={carbs} onChange={setCarbs} min={0} max={9999} decimal />}
      <CategoryTabs value={cat} onChange={setCat} label={t('scan_meal')} />
      <Button block disabled={!name.trim() || kcal == null} onClick={() => onLog(name.trim(), cat, Math.round(kcal ?? 0), protein ?? 0, carbs ?? 0)}>{t('food_log_it')}</Button>
    </Sheet>
  )
}

function FoodEditorSheet({ food, places, carbsOn, onSave, onDelete, onClose }: { food: FoodRow | null; places: string[]; carbsOn: boolean; onSave: (f: FoodRow) => void; onDelete: () => void; onClose: () => void }) {
  const [name, setName] = useState('')
  const [kcal, setKcal] = useState<number | null>(null)
  const [protein, setProtein] = useState<number | null>(null)
  const [carbs, setCarbs] = useState<number | null>(null)
  const [serving, setServing] = useState('')
  const [cat, setCat] = useState<FoodCategory>('LUNCH')
  const [pinned, setPinned] = useState(false)
  const [place, setPlace] = useState('')
  const [parts, setParts] = useState<FoodPart[]>([])
  const [editParts, setEditParts] = useState(false)
  const seen = useRef<FoodRow | null>(null)
  if (food && seen.current !== food) {
    seen.current = food
    setName(food.name); setKcal(numText(food.kcal)); setProtein(numText(food.protein)); setCarbs(numText(food.carbs)); setServing(food.serving); setCat(food.category); setPinned(food.pinned)
    setPlace(food.place ?? ''); setParts(food.parts ?? []); setEditParts(false)
  }
  const onParts = parts.filter((p) => p.on)
  if (!food) seen.current = null
  return (
    <Sheet open={food != null} onClose={onClose} title={food?.id ? t('food_edit_title') : t('food_add_title')}>
      <TextField label={t('label_name')} value={name} onInput={(v) => setName(v.slice(0, 48))} />
      {parts.length > 0 ? (
        // With parts, the numbers come from the ticked parts: change a part's grams instead of typing totals.
        <button type="button" class="food-parts-card" onClick={() => setEditParts(true)}>
          <span>{t('food_kcal', onParts.reduce((a, p) => a + p.kcal, 0))} · {t('food_g_protein', Math.round(onParts.reduce((a, p) => a + p.protein, 0)))}</span>
          <span class="food-row-parts-text">{partsCount(parts)} · {t('food_parts_change')}</span>
        </button>
      ) : (
        <>
          <div class="food-two">
            <NumberField label={t('home_legend_calories')} value={kcal} onChange={setKcal} min={0} max={99999} />
            <NumberField label={t('home_legend_protein')} value={protein} onChange={setProtein} min={0} max={9999} decimal />
          </div>
          {carbsOn && <NumberField label={t('label_carbs_g')} value={carbs} onChange={setCarbs} min={0} max={9999} decimal />}
        </>
      )}
      <PlaceField value={place} onChange={setPlace} places={places} />
      <TextField label={t('food_serving_hint')} value={serving} onInput={(v) => setServing(v.slice(0, 32))} />
      <CategoryTabs value={cat} onChange={setCat} label={t('scan_meal')} />
      <div class="food-switch-row"><span>{t('food_pin_top')}</span><Switch on={pinned} onChange={setPinned} label={t('food_w_pinned_switch')} /></div>
      <div class="food-sheet-actions">
        {food?.id ? <Button variant="text" class="kit-danger" onClick={onDelete}>{t('action_delete')}</Button> : <span />}
        <Button disabled={!name.trim() || (kcal == null && !onParts.length)} onClick={() => food && onSave(withParts({ ...food, name: name.trim(), kcal: Math.round(kcal ?? 0), protein: protein ?? 0, carbs: carbs ?? 0, serving: serving.trim(), category: cat, pinned, place: place.trim() }, parts))}>{t('action_save')}</Button>
      </div>
      <PartsSheet open={editParts} foodName={name || food?.name || ''} parts={parts} carbsOn={carbsOn} onSave={(p) => { setParts(p); setEditParts(false) }} onClose={() => setEditParts(false)} />
    </Sheet>
  )
}

export function FoodScreen(_: { param?: string }) {
  const s = useSettings()
  const foods = useFoods()
  const [day, setDay] = useState(todayEpochDay())
  const [followToday, setFollowToday] = useState(true)
  const [cat, setCat] = useState<FoodCategory>(categoryForNow())
  /** foodId → how much and the meal it's logged under, for foods ticked but not yet logged. */
  const [sel, setSel] = useState<Record<number, { qty: number; cat: FoodCategory }>>({})
  const [query, setQuery] = useState('')
  const [partsFor, setPartsFor] = useState<FoodRow | null>(null)
  const usage = useFoodUsage()
  const [editing, setEditing] = useState<FoodRow | null>(null)
  const [confirmDel, setConfirmDel] = useState<FoodRow | null>(null)
  const [quick, setQuick] = useState(false)
  const [undo, setUndo] = useState<{ text: string; meal: MealRow } | null>(null)
  const meals = useMealsOn(day)
  const today = todayEpochDay()
  const isToday = day === today

  // A tab left open overnight moves on to the new day while the person is on "today".
  useEffect(() => { if (followToday && day !== today) setDay(today) }, [today, followToday, day])
  useEffect(() => {
    if (!undo) return
    const id = setTimeout(() => setUndo(null), 5000)
    return () => clearTimeout(id)
  }, [undo])

  const shift = (d: number) => { const next = Math.min(today, day + d); setDay(next); setFollowToday(next === today) }
  const inCat = foods.filter((f) => f.category === cat)
  const searching = query.trim() !== ''
  // Best match first; among equally good matches, pinned foods, then the ones you log most and most lately.
  const results = searching
    ? foods.map((f) => [f, score(query, f.name, f.place ?? '')] as const).filter(([, sc]) => sc > 0)
      .sort(([a, sa], [b, sb]) => sb - sa || Number(b.pinned) - Number(a.pinned) || (usage.get(b.id!)?.times ?? 0) - (usage.get(a.id!)?.times ?? 0) || (usage.get(b.id!)?.lastAt ?? 0) - (usage.get(a.id!)?.lastAt ?? 0))
      .map(([f]) => f)
    : []
  const places = placesOf(foods)
  // Restaurant shortcuts: the ones used most lately first.
  const lastAt = (f: FoodRow) => usage.get(f.id!)?.lastAt ?? f.createdAt
  const recentPlaces = places.map((p) => [p, Math.max(...foods.filter((f) => f.place?.trim() === p).map(lastAt))] as const).sort((a, b) => b[1] - a[1]).map(([p]) => p).slice(0, 6)
  const shown = searching ? results : inCat
  const picked = foods.filter((f) => f.id != null && sel[f.id] != null)
  // Each food rounded on its own, as logFoods stores it, so this is what the day's total gains.
  const selKcal = picked.reduce((a, f) => a + scaledKcal(f.kcal, sel[f.id!].qty), 0)
  const selProtein = Math.round(picked.reduce((a, f) => a + f.protein * sel[f.id!].qty, 0))
  const dots = new Set(Object.values(sel).map((p) => p.cat))

  // Drinks log under Drinks; a search result under the open tab (it opens on the meal for the time of day); otherwise its own tab.
  const mealFor = (f: FoodRow): FoodCategory => (f.category === 'DRINKS' ? 'DRINKS' : searching ? cat : f.category)
  const setQty = (f: FoodRow, q: number) => setSel((m) => {
    const { [f.id!]: prev, ...rest } = m
    return q <= 0 ? rest : { ...rest, [f.id!]: { qty: q, cat: prev?.cat ?? mealFor(f) } }
  })

  const logSelected = async () => {
    if (!picked.length) return
    const sep = t('food_list_sep')
    await logFoods(picked.map((f) => ({ food: f, qty: sel[f.id!].qty, category: sel[f.id!].cat, name: logName(f, (n, off) => t('food_without', n, off), sep) })), day)
    setSel({})
    toast(plural('food_logged_items', picked.length) + (await badgeSuffix()))
  }
  const quickLog = async (name: string, c: FoodCategory, kcal: number, protein: number, carbs: number) => {
    await logOnce(name, c, kcal, protein, day, carbs)
    setQuick(false)
    toast(t('food_logged_name', name))
  }
  const removeMeal = async (m: MealRow) => { await deleteMeal(m.id!); setUndo({ text: t('food_removed', m.name), meal: m }) }
  const doUndo = async () => { if (undo) await restoreMeal(undo.meal); setUndo(null) }
  const openAdd = () => setEditing(blankFood(cat))

  return (
    <div class={`content food${picked.length ? ' food-has-bar' : ''}`}>
      <div class="food-head">
        <span class="food-head-fill" />
        <Button icon={<CameraIcon size={20} />} onClick={() => { location.hash = '#/scan' }}>{t('food_scan_meal')}</Button>
        <IconButton label={t('food_add')} tone="tonal" onClick={openAdd}><AddIcon /></IconButton>
      </div>
      <DayPicker day={day} today={today} onShift={shift} onToday={() => { setDay(today); setFollowToday(true) }} />
      <Totals meals={meals} kcalGoal={s.kcalGoal} proteinGoal={s.proteinGoal} carbsGoal={s.carbsGoal} trackCarbs={s.trackCarbs} />
      {isToday && <WeekCard proteinGoal={s.proteinGoal} trackCarbs={s.trackCarbs} />}
      <div class="food-search">
        <SearchIcon size={20} />
        <input
          type="search" class="food-search-input" value={query} placeholder={t('food_search_hint')} aria-label={t('food_search_hint')} enterkeyhint="search"
          onInput={(e) => setQuery((e.target as HTMLInputElement).value.slice(0, 48))}
          onKeyDown={(e) => { if (e.key === 'Enter') (e.target as HTMLInputElement).blur() }}
        />
        {query && <IconButton label={t('food_search_clear')} onClick={() => setQuery('')}><CloseIcon size={18} /></IconButton>}
      </div>
      {!searching && recentPlaces.length > 0 && (
        <div class="food-places">
          <span class="food-note">{t('food_restaurants')}</span>
          <div class="food-chips">{recentPlaces.map((p) => <Chip key={p} label={p} onClick={() => setQuery(p)} />)}</div>
        </div>
      )}
      <CategoryTabs value={cat} onChange={setCat} dots={dots} label={t('scan_meal')} />
      {searching && <p class="food-note food-search-note">{results.length ? t('food_search_logs_as', catLabel(cat)) : t('food_search_none', query.trim())}</p>}
      {searching && !results.length && (
        <div><Button variant="tonal" icon={<AddIcon size={20} />} onClick={() => setEditing({ ...blankFood(cat), name: query.trim().slice(0, 48) })}>{t('food_search_add', query.trim())}</Button></div>
      )}
      {!searching && inCat.length === 0 && (
        <Card>
          <div class="food-empty">
            <RestaurantIcon size={32} />
            <h3>{t('food_none_in_category', catLabel(cat))}</h3>
            <p>{t('food_empty_hint')}</p>
            <Button variant="tonal" icon={<AddIcon size={20} />} onClick={openAdd}>{t('food_add_a_food')}</Button>
          </div>
        </Card>
      )}
      {shown.map((f) => <FoodItem key={f.id} food={f} qty={sel[f.id!]?.qty} onToggle={() => setQty(f, sel[f.id!] == null ? 1 : 0)} onQty={(q) => setQty(f, q)} onEdit={() => setEditing(f)} onParts={f.parts?.length ? () => setPartsFor(f) : undefined} />)}
      <div class="food-logged-head">
        <h2>{isToday ? t('food_logged_today') : t('food_logged_that_day')}</h2>
        <Button variant="tonal" icon={<AddIcon size={20} />} onClick={() => setQuick(true)}>{t('food_quick_log')}</Button>
      </div>
      {meals.length === 0 && <p class="food-note">{isToday ? t('food_nothing_today') : t('food_nothing_that_day')}</p>}
      {meals.map((m) => <SwipeRow key={m.id} label={t('action_delete')} onDelete={() => removeMeal(m)}><MealItem meal={m} onDelete={() => removeMeal(m)} /></SwipeRow>)}

      {picked.length > 0 && (
        <div class="food-bar"><Button block onClick={logSelected}>{plural('food_log_selection', picked.length, picked.length, fmtInt(selKcal), selProtein)}</Button></div>
      )}
      {undo && <div class="food-snack" role="status"><span>{undo.text}</span><button type="button" onClick={doUndo}>{t('action_undo')}</button></div>}

      <QuickLogSheet open={quick} initial={cat} carbsOn={s.trackCarbs} onLog={quickLog} onClose={() => setQuick(false)} />
      <PartsSheet open={partsFor != null} foodName={partsFor?.name ?? ''} parts={partsFor?.parts ?? []} carbsOn={s.trackCarbs}
        onSave={async (p) => { if (partsFor) await saveFood(withParts(partsFor, p)); setPartsFor(null) }} onClose={() => setPartsFor(null)} />
      <FoodEditorSheet
        food={editing} places={places} carbsOn={s.trackCarbs} onClose={() => setEditing(null)}
        onSave={async (f) => { await saveFood(f); setEditing(null) }}
        onDelete={() => { setConfirmDel(editing); setEditing(null) }}
      />
      <Dialog
        open={confirmDel != null} destructive title={t('food_delete_title', confirmDel?.name ?? '')} message={t('food_delete_body')} confirmLabel={t('action_delete')}
        onCancel={() => setConfirmDel(null)}
        onConfirm={async () => { const f = confirmDel; setConfirmDel(null); if (f?.id != null) { await deleteFood(f.id); setSel((m) => { const { [f.id!]: _d, ...rest } = m; return rest }) } }}
      />
    </div>
  )
}
