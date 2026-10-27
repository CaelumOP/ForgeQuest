import { useEffect, useRef, useState } from 'preact/hooks'
import { t } from '../../i18n'
import { todayEpochDay } from '../../domain/dates'
import { scaledKcal } from '../../domain/nutrition'
import type { FoodCategory } from '../../data/db'
import { AiError, aiErrorKey } from '../../data/ai'
import { categoryForNow, logOnce, placesOf, saveMeal, useFoods, withParts } from '../../data/food'
import type { FoodPart } from '../../data/db'
import { PlaceField } from './FoodParts'
import { useSettings } from '../../data/settings'
import { CameraIcon, CheckIcon, PhotoLibraryIcon } from '../icons'
import { Button, Card, ChipGroup, NumberField, Sheet, toast } from '../kit'
import { goBack } from '../nav'
import { TopBar } from '../TopBar'
import { buzzError } from '../live/feedback'
import { badgeSuffix, catLabel } from './FoodScreen'
import { COOKING_FATS, PORTIONS, scanMeal, scanTotals, shrinkPhoto, withGrams, type FoodScan, type ScanItem } from './scan'
import './food.css'

const SCAN_LIMIT_MS = 25_000
const CATS: FoodCategory[] = ['BREAKFAST', 'LUNCH', 'DINNER', 'SNACKS', 'DRINKS']
const STEPS = ['scan_step_find', 'scan_step_portions', 'scan_step_add']

type Photo = { url: string; base64: string }
type State =
  | { kind: 'pick' }
  | { kind: 'busy'; photo: Photo | null }
  | { kind: 'result'; photo: Photo; scan: FoodScan }
  | { kind: 'failed'; photo: Photo | null; message: string; keyProblem: boolean }

function Switch({ on, onChange, label }: { on: boolean; onChange: (v: boolean) => void; label: string }) {
  return <button type="button" class="food-switch" role="switch" aria-checked={on} aria-label={label} onClick={() => onChange(!on)} />
}

function Result({ scan, carbsOn, onRetake, onLogged }: { scan: FoodScan; carbsOn: boolean; onRetake: () => void; onLogged: () => void }) {
  const [items, setItems] = useState<ScanItem[]>(scan.items)
  const [dish, setDish] = useState(scan.dish)
  const [included, setIncluded] = useState<Set<number>>(new Set(scan.items.map((_, i) => i)))
  const [editing, setEditing] = useState<number | null>(null)
  const [grams, setGrams] = useState<number | null>(null)
  const [portion, setPortion] = useState('normal')
  const [fat, setFat] = useState('light')
  const [cat, setCat] = useState<FoodCategory>(categoryForNow())
  const [save, setSave] = useState(false)
  const [pin, setPin] = useState(false)
  const [place, setPlace] = useState('')
  const [busy, setBusy] = useState(false)
  const places = placesOf(useFoods())

  const chosen = items.filter((_, i) => included.has(i))
  const tot = scanTotals(chosen, PORTIONS.find((p) => p.id === portion)!.factor, COOKING_FATS.find((f) => f.id === fat)!.kcal)
  const toggle = (i: number) => setIncluded((s) => { const n = new Set(s); if (!n.delete(i)) n.add(i); return n })
  const openGrams = (i: number) => { setEditing(i); setGrams(Math.max(1, items[i].grams)) }

  const log = async () => {
    if (busy || !chosen.length) return
    setBusy(true)
    const name = dish.trim() || scan.dish
    const kcal = tot.kcal
    const protein = tot.protein
    const carbs = tot.carbs
    // Parts at the chosen portion; extra cooking fat is a part of its own.
    const factor = PORTIONS.find((p) => p.id === portion)!.factor
    const fatKcal = COOKING_FATS.find((f) => f.id === fat)!.kcal
    const parts: FoodPart[] = items.map((it, i) => ({ name: it.name, grams: Math.round(it.grams * factor), kcal: scaledKcal(it.kcal, factor), protein: it.protein * factor, carbs: it.carbs * factor, on: included.has(i) }))
    if (fatKcal > 0) parts.push({ name: t('scan_extra_fat_part'), grams: 0, kcal: fatKcal, protein: 0, carbs: 0, on: true })
    // The day's log says what was left out: "Chicken shawarma, no Ketchup".
    const off = parts.filter((p) => !p.on).map((p) => p.name)
    const logged = off.length ? t('food_without', name, off.join(t('food_list_sep'))) : name
    try {
      await logOnce(logged, cat, kcal, protein, todayEpochDay(), carbs, true)
      // Saved with its parts, so next time it's one tap with ketchup ticked off; the same meal is updated, not added twice.
      if (save) await saveMeal(withParts({ name, category: cat, kcal, protein, carbs, serving: t('scan_default_serving'), photoPath: null, pinned: pin, createdAt: Date.now(), place: place.trim() }, parts))
      toast(t('scan_logged_summary', name, kcal, Math.round(protein)) + (await badgeSuffix()))
      onLogged()
    } catch { setBusy(false) }
  }

  return (
    <>
      <div class="scan-sum">
        <div class="scan-dish">
          <input class="kit-input" aria-label={t('scan_tap_name')} value={dish} onInput={(e) => setDish((e.target as HTMLInputElement).value.slice(0, 48).replace(/\n/g, ''))} />
          <span class="food-note">{t('scan_tap_name')}</span>
        </div>
        <div class="scan-nums">
          <span class="k">{t('food_kcal', tot.kcal)}</span>
          <span class="p">{t('food_g_protein', Math.round(tot.protein))}</span>
          {carbsOn && <span class="c">{t('food_g_carbs', Math.round(tot.carbs))}</span>}
        </div>
      </div>
      {scan.handVisible && <p class="scan-hand">{t('scan_hand_scaled')}</p>}
      {scan.note && <p class="food-note">{scan.note}</p>}
      {items.map((item, i) => {
        const on = included.has(i)
        return (
          <div key={i} class="scan-item">
            <button type="button" class="scan-item-tick" aria-pressed={on} aria-label={on ? t('cd_included') : t('cd_excluded')} onClick={() => toggle(i)}>
              <span>{on && <CheckIcon size={16} />}</span>
            </button>
            <button type="button" class={`scan-item-main${on ? '' : ' off'}`} onClick={() => openGrams(i)}>
              <span class="scan-item-name">{item.name}</span>
              <span class="scan-item-sub">{item.grams > 0 ? t('scan_about_grams', item.grams) : t('scan_set_grams')}</span>
            </button>
            <div class={`scan-item-nums${on ? '' : ' off'}`}>
              <span class="k">{t('food_kcal', item.kcal)}</span>
              <span class="p">{t('food_g', Math.round(item.protein))}</span>
            </div>
          </div>
        )
      })}
      <p class="scan-label">{t('scan_portion_size')}</p>
      <ChipGroup label={t('scan_portion_size')} value={portion} onChange={setPortion}
        options={PORTIONS.map((p) => ({ value: p.id, label: `${t(p.id === 'small' ? 'scan_portion_small' : p.id === 'large' ? 'scan_portion_large' : 'scan_normal')} ${p.factor}×`.replace('.0×', '×') }))} />
      <p class="scan-label">{t('scan_extra_fat')}</p>
      <ChipGroup label={t('scan_extra_fat')} value={fat} onChange={setFat}
        options={COOKING_FATS.map((f) => ({ value: f.id, label: `${t(f.id === 'light' ? 'scan_fat_light' : f.id === 'heavy' ? 'scan_fat_heavy' : 'scan_normal')} +${f.kcal}` }))} />
      <p class="scan-label">{t('scan_meal')}</p>
      <ChipGroup label={t('scan_meal')} value={cat} onChange={setCat} options={CATS.map((c) => ({ value: c, label: catLabel(c) }))} />
      <div class="food-switch-row">
        <span><span>{t('scan_save_to_foods')}</span><br /><span class="food-note">{t('scan_save_hint')}</span></span>
        <Switch on={save} onChange={setSave} label={t('scan_save_to_foods')} />
      </div>
      {save && <div class="food-switch-row"><span>{t('food_pin_top')}</span><Switch on={pin} onChange={setPin} label={t('food_w_pinned_switch')} /></div>}
      {save && <PlaceField value={place} onChange={setPlace} places={places} />}
      <div class="scan-actions">
        <Button variant="tonal" onClick={onRetake}>{t('scan_retake')}</Button>
        <Button disabled={!chosen.length || busy} loading={busy} onClick={log}>{t('scan_log_meal')}</Button>
      </div>
      <Sheet open={editing != null} onClose={() => setEditing(null)} title={t('scan_grams_title', editing != null ? items[editing].name : '')}>
        <NumberField label={t('unit_g')} value={grams} onChange={setGrams} min={1} max={3000} />
        <Button disabled={grams == null} onClick={() => { if (editing != null && grams != null) setItems((xs) => xs.map((x, i) => (i === editing ? withGrams(x, grams) : x))); setEditing(null) }}>{t('action_save')}</Button>
      </Sheet>
    </>
  )
}

export function ScanScreen(_: { param?: string }) {
  const s = useSettings()
  const [state, setState] = useState<State>({ kind: 'pick' })
  const [step, setStep] = useState(0)
  const photoRef = useRef<Photo | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const camera = useRef<HTMLInputElement>(null)
  const gallery = useRef<HTMLInputElement>(null)
  const runId = useRef(0)
  const hasKey = s.apiKey.trim() !== ''

  const drop = () => {
    abortRef.current?.abort()
    abortRef.current = null
    if (photoRef.current) URL.revokeObjectURL(photoRef.current.url)
    photoRef.current = null
  }
  // The photo only lives in memory: gone when the screen closes.
  useEffect(() => drop, [])
  useEffect(() => {
    if (state.kind !== 'busy') return
    setStep(0)
    const id = setInterval(() => setStep((n) => Math.min(STEPS.length - 1, n + 1)), 3000)
    return () => clearInterval(id)
  }, [state.kind])

  const analyze = async (photo: Photo) => {
    const me = ++runId.current
    const ctl = new AbortController()
    abortRef.current = ctl
    let slow = false
    const timer = setTimeout(() => { slow = true; ctl.abort() }, SCAN_LIMIT_MS)
    setState({ kind: 'busy', photo })
    try {
      const scan = await scanMeal(photo.base64, ctl.signal)
      if (me === runId.current) setState({ kind: 'result', photo, scan })
    } catch (e) {
      if (me !== runId.current) return
      buzzError()
      const { key, args } = slow ? { key: 'scan_err_slow', args: [] } : e instanceof AiError ? aiErrorKey(e) : { key: 'scan_err_generic', args: [] }
      setState({ kind: 'failed', photo, message: t(key, ...args), keyProblem: e instanceof AiError && (e.kind === 'bad_key' || e.kind === 'no_key') })
    } finally { clearTimeout(timer) }
  }

  const onFile = async (e: Event) => {
    const input = e.target as HTMLInputElement
    const file = input.files?.[0]
    input.value = ''
    if (!file) return
    drop()
    setState({ kind: 'busy', photo: null })
    try {
      const { blob, base64 } = await shrinkPhoto(file)
      const photo = { url: URL.createObjectURL(blob), base64 }
      photoRef.current = photo
      await analyze(photo)
    } catch {
      setState({ kind: 'failed', photo: null, message: t('food_w_photo_bad'), keyProblem: false })
    }
  }

  const retake = () => { runId.current++; drop(); setState({ kind: 'pick' }) }
  const photo = state.kind === 'pick' ? null : state.photo

  return (
    <>
      <TopBar title={t('food_scan_meal')} onBack={goBack} />
      <div class="scroll">
        <div class="content scan">
          <input ref={camera} class="scan-file" type="file" accept="image/*" capture="environment" tabIndex={-1} aria-hidden="true" onChange={onFile} />
          <input ref={gallery} class="scan-file" type="file" accept="image/*" tabIndex={-1} aria-hidden="true" onChange={onFile} />
          {photo && <img class="scan-photo" src={photo.url} alt={t('food_photo')} />}

          {state.kind === 'pick' && (hasKey ? (
            <div class="scan-start">
              <p>{t('scan_frame_hint')}</p>
              <Button icon={<CameraIcon size={20} />} onClick={() => camera.current?.click()}>{t('food_w_take_photo')}</Button>
              <Button variant="tonal" icon={<PhotoLibraryIcon size={20} />} onClick={() => gallery.current?.click()}>{t('photo_choose_gallery')}</Button>
              <p>{t('food_w_scan_privacy')}</p>
            </div>
          ) : (
            <Card>
              <div class="scan-nokey">
                <p>{t('ai_key_needed_food')}</p>
                <Button onClick={() => { location.hash = '#/profile' }}>{t('ai_key_needed_button')}</Button>
              </div>
            </Card>
          ))}

          {state.kind === 'busy' && (
            <div class="scan-busy" role="status" aria-live="polite">
              <span class="kit-spinner" aria-hidden="true" />
              <span>{t(STEPS[step])}</span>
            </div>
          )}

          {state.kind === 'failed' && (
            <Card>
              <div class="scan-fail">
                <h2>{t('scan_failed_title')}</h2>
                <p role="alert">{state.message}</p>
                <div class="scan-actions">
                  <Button variant="tonal" onClick={retake}>{t('scan_retake')}</Button>
                  {state.keyProblem
                    ? <Button onClick={() => { location.hash = '#/profile' }}>{t('scan_open_profile')}</Button>
                    : state.photo && <Button onClick={() => analyze(state.photo!)}>{t('action_try_again')}</Button>}
                </div>
              </div>
            </Card>
          )}

          {state.kind === 'result' && <Result scan={state.scan} carbsOn={s.trackCarbs} onRetake={retake} onLogged={goBack} />}
        </div>
      </div>
    </>
  )
}
