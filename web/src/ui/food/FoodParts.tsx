import { useEffect, useState } from 'preact/hooks'
import { t } from '../../i18n'
import type { FoodPart } from '../../data/db'
import { partWithGrams } from '../../data/food'
import { samePlace, score } from '../../domain/food-search'
import { CheckIcon } from '../icons'
import { Button, Chip, NumberField, Sheet, TextField } from '../kit'

/** "Parts: 4 of 5" for a food with parts. */
export const partsCount = (parts: readonly FoodPart[]): string => t('food_parts_count', parts.filter((p) => p.on).length, parts.length)

/** Ticks for each part (on/off) and a tap on a part to set its grams; like the scan review list (port of FoodParts.kt). */
function PartsList({ parts, onChange }: { parts: FoodPart[]; onChange: (p: FoodPart[]) => void }) {
  const [editing, setEditing] = useState<number | null>(null)
  const [grams, setGrams] = useState<number | null>(null)
  const set = (i: number, p: FoodPart) => onChange(parts.map((x, j) => (j === i ? p : x)))
  return (
    <>
      {parts.map((part, i) => (
        <div key={i} class="food-part">
          <div class="scan-item">
            <button type="button" class="scan-item-tick" aria-pressed={part.on} aria-label={part.on ? t('cd_included') : t('cd_excluded')} onClick={() => set(i, { ...part, on: !part.on })}>
              <span>{part.on && <CheckIcon size={16} />}</span>
            </button>
            <button type="button" class={`scan-item-main${part.on ? '' : ' off'}`} onClick={() => { setEditing(editing === i ? null : i); setGrams(Math.max(1, part.grams)) }}>
              <span class="scan-item-name">{part.name}</span>
              <span class="scan-item-sub">{part.grams > 0 ? t('scan_about_grams', part.grams) : t('scan_set_grams')}</span>
            </button>
            <div class={`scan-item-nums${part.on ? '' : ' off'}`}>
              <span class="k">{t('food_kcal', part.kcal)}</span>
              <span class="p">{t('food_g', Math.round(part.protein))}</span>
            </div>
          </div>
          {editing === i && (
            <div class="food-part-grams">
              <NumberField label={t('scan_grams_title', part.name)} value={grams} onChange={setGrams} min={1} max={3000} />
              <Button variant="tonal" disabled={grams == null} onClick={() => { if (grams != null) set(i, partWithGrams(part, Math.round(grams))); setEditing(null) }}>{t('action_save')}</Button>
            </div>
          )}
        </div>
      ))}
    </>
  )
}

/**
 * The parts of one food (bread, chicken, ketchup) to tick on or off. `onSave` gets them back with unticked ones switched
 * off rather than removed, so they can be ticked again next time.
 */
export function PartsSheet({ open, foodName, parts, carbsOn, onSave, onClose }: { open: boolean; foodName: string; parts: FoodPart[]; carbsOn: boolean; onSave: (p: FoodPart[]) => void; onClose: () => void }) {
  const [draft, setDraft] = useState<FoodPart[]>(parts)
  useEffect(() => { if (open) setDraft(parts) }, [open])
  const on = draft.filter((p) => p.on)
  return (
    <Sheet open={open} onClose={onClose} title={t('food_parts_title', foodName)}>
      <p class="food-note">{t('food_parts_hint')}</p>
      <div class="food-part-sum">
        <span class="k">{t('food_kcal', on.reduce((a, p) => a + p.kcal, 0))}</span>
        <span class="p">{t('food_g_protein', Math.round(on.reduce((a, p) => a + p.protein, 0)))}</span>
        {carbsOn && <span class="c">{t('food_g_carbs', Math.round(on.reduce((a, p) => a + p.carbs, 0)))}</span>}
      </div>
      <PartsList parts={draft} onChange={setDraft} />
      <Button block disabled={!on.length} onClick={() => onSave(draft)}>{t('action_save')}</Button>
    </Sheet>
  )
}

/**
 * The optional restaurant field. While typing, restaurants already used come up as "Same as …?" (spelling variants and
 * venue words like كفتيريا or مطعم aside); a tap uses that exact name, so one place isn't saved under two spellings.
 */
export function PlaceField({ value, onChange, places }: { value: string; onChange: (v: string) => void; places: string[] }) {
  const typed = value.trim()
  const suggestions = typed.length < 2 ? [] : places.filter((p) => p !== typed && (score(typed, '', p) > 0 || samePlace(typed, p, places))).slice(0, 3)
  return (
    <>
      <TextField label={t('food_place')} value={value} onInput={(v) => onChange(v.slice(0, 48).replace(/\n/g, ''))} />
      {suggestions.length > 0 && (
        <div class="food-chips">{suggestions.map((p) => <Chip key={p} label={t('food_place_same_as', p)} onClick={() => onChange(p)} />)}</div>
      )}
    </>
  )
}
