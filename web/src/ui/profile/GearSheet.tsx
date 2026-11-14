import { useState } from 'preact/hooks'
import { t, useLang } from '../../i18n'
import {
  EQUIPMENT_GROUPS,
  EQUIPMENT_GROUP_LABEL,
  EQUIPMENT_META,
  TRAINING_PLACES,
  TRAINING_PLACE_INFO,
  encodeEquipmentSet,
  expandLegacyEquipment,
  matchTrainingPlace,
  SELECTABLE_EQUIPMENT,
  type Equipment,
  type EquipmentGroup,
  type TrainingPlace,
} from '../../domain/exercise-swap-engine'
import { Button, Card, Segmented, Sheet, haptics } from '../kit'
import { CheckIcon, ForwardChevron } from '../icons'
import { gearCustomizeSummary } from './helpers'
import './gear.css'

function TuneIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false">
      <path d="M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z" />
    </svg>
  )
}

function gearLabel(eq: Equipment, ar: boolean): string {
  const m = EQUIPMENT_META[eq]
  return ar ? m.ar : m.en
}

function groupLabel(g: EquipmentGroup, ar: boolean): string {
  const m = EQUIPMENT_GROUP_LABEL[g]
  return ar ? m.ar : m.en
}

/** Three-way preset bar (Full gym / Home / Bodyweight only). Highlights none when a custom subset is selected. */
export function TrainingPlaceSelector({
  equipment,
  onPick,
}: {
  equipment: ReadonlySet<Equipment>
  onPick: (place: TrainingPlace, next: Set<Equipment>) => void
}) {
  const ar = useLang() === 'ar'
  const clean = expandLegacyEquipment(equipment)
  const matched = clean.size > 0 ? matchTrainingPlace(clean) : null
  const options = TRAINING_PLACES.map((p) => ({
    value: p,
    label: ar ? TRAINING_PLACE_INFO[p].labelAr : TRAINING_PLACE_INFO[p].labelEn,
  }))
  return (
    <Segmented
      label={t('pb_equipment')}
      value={matched}
      options={options}
      onChange={(p: TrainingPlace) => {
        haptics.lightTick()
        onPick(p, new Set<Equipment>(TRAINING_PLACE_INFO[p].equipment))
      }}
    />
  )
}

/** Tappable summary row opening `GearSheet`, showing "Customize gear · Full gym (24 items)" etc. */
export function GearCustomizeRow({
  equipment,
  onOpen,
}: {
  equipment: ReadonlySet<Equipment>
  onOpen: () => void
}) {
  useLang()
  return (
    <button type="button" class="gear-row-btn" onClick={onOpen}>
      <span class="gear-row-ic"><TuneIcon /></span>
      <span class="gear-row-text">{gearCustomizeSummary(equipment)}</span>
      <span class="gear-row-chev"><ForwardChevron size={20} /></span>
    </button>
  )
}

/** Granular equipment picker sheet with 5 grouped grids, per-group select-all/clear, and live updates. */
export function GearSheet({
  open,
  equipment,
  onUpdate,
  onClose,
}: {
  open: boolean
  equipment: ReadonlySet<Equipment>
  onUpdate: (next: Set<Equipment>) => void
  onClose: () => void
}) {
  const ar = useLang() === 'ar'
  const clean = expandLegacyEquipment(equipment)

  const commit = (next: Set<Equipment>) => {
    const out = expandLegacyEquipment(next)
    out.add('BODYWEIGHT')
    onUpdate(out)
  }

  return (
    <Sheet open={open} onClose={onClose} title={t('equipment_title')}>
      <div class="gear-sheet">
        <p class="gear-hint">{t('equipment_hint')}</p>
        <div class="gear-always">{t('equipment_always')}</div>

        <TrainingPlaceSelector
          equipment={clean}
          onPick={(_, next) => commit(next)}
        />

        {EQUIPMENT_GROUPS.map((group) => {
          const items = SELECTABLE_EQUIPMENT.filter((eq) => EQUIPMENT_META[eq].group === group)
          const selectedCount = items.filter((eq) => clean.has(eq)).length
          const allSelected = selectedCount === items.length && items.length > 0
          return (
            <section key={group} class="gear-group">
              <div class="gear-group-head">
                <h3 class="gear-group-title">{groupLabel(group, ar)}</h3>
                <span class="gear-group-count">{t('equipment_group_count', selectedCount, items.length)}</span>
                <button
                  type="button"
                  class="gear-all-btn"
                  onClick={() => {
                    haptics.lightTick()
                    const next = new Set(clean)
                    if (allSelected) {
                      for (const eq of items) next.delete(eq)
                    } else {
                      for (const eq of items) next.add(eq)
                    }
                    commit(next)
                  }}
                >
                  {t(allSelected ? 'action_clear' : 'equipment_select_all')}
                </button>
              </div>
              <div class="gear-grid">
                {items.map((eq) => {
                  const on = clean.has(eq)
                  return (
                    <button
                      key={eq}
                      type="button"
                      class={`gear-tile${on ? ' on' : ''}`}
                      aria-pressed={on}
                      onClick={() => {
                        haptics.lightTick()
                        const next = new Set(clean)
                        if (on) next.delete(eq); else next.add(eq)
                        commit(next)
                      }}
                    >
                      <span class="gear-tile-check" aria-hidden="true">
                        {on && <CheckIcon size={16} />}
                      </span>
                      <span class="gear-tile-label">{gearLabel(eq, ar)}</span>
                    </button>
                  )
                })}
              </div>
            </section>
          )
        })}

        <div class="gear-done">
          <Button block onClick={onClose}>{t('cd_done')}</Button>
        </div>
      </div>
    </Sheet>
  )
}

/** Equipment section for PlanEditorScreen: place preset pills + Customize gear row + sheet. */
export function PlanEquipmentRow({
  equipment,
  onSave,
}: {
  equipment: ReadonlySet<Equipment>
  onSave: (encoded: string) => unknown
}) {
  const [open, setOpen] = useState(false)
  const clean = expandLegacyEquipment(equipment)
  const commit = (next: Set<Equipment>) => void onSave(encodeEquipmentSet(next))

  return (
    <Card>
      <div class="gear-card">
        <div class="gear-card-head">
          <h3 class="gear-card-title">{t('equipment_title')}</h3>
          <p class="gear-hint">{t('equipment_hint')}</p>
        </div>
        <TrainingPlaceSelector equipment={clean} onPick={(_, next) => commit(next)} />
        <GearCustomizeRow equipment={clean} onOpen={() => setOpen(true)} />
      </div>
      <GearSheet open={open} equipment={clean} onUpdate={commit} onClose={() => setOpen(false)} />
    </Card>
  )
}
