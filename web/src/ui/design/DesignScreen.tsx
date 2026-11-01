import { useEffect, useState } from 'preact/hooks'
import { TopBar } from '../TopBar'
import { AddIcon } from '../icons'
import { goBack } from '../nav'
import {
  Button, Card, Chip, ChipGroup, EmptyState, ListRow, NumberField, Ring, Segmented, SectionHeader, Stat, Stepper, TextField,
} from '../kit'
import './design.css'

// Hidden dev catalogue (#/design) of the Soft Coach tokens and kit (DESIGN.md). Labels are English on purpose.
const colors = ['bg', 'card', 'card-high', 'divider', 'text', 'text-2', 'teal', 'teal-track', 'on-teal', 'blue', 'gold', 'purple', 'coral']
const type = [
  ['Display 34/40', 34, 40, 600], ['Headline 24/32', 24, 32, 600], ['Title 20/26', 20, 26, 600], ['Section 16/22', 16, 22, 500],
  ['Body 16/24', 16, 24, 400], ['Body small 14/22', 14, 22, 400], ['Label 14/20', 14, 20, 500], ['Caption 12/16', 12, 16, 400],
] as const
const radii = [['sm 12', '--r-sm'], ['md 20', '--r-md'], ['lg 28', '--r-lg'], ['full', '--r-full']]
const spaces = [['xxs 4', 4], ['xs 8', 8], ['sm 12', 12], ['md 16', 16], ['lg 24', 24], ['xl 32', 32], ['xxl 48', 48]] as const
const motion = [
  ['--ease-standard', 'cubic-bezier(0.2, 0, 0, 1)'], ['--ease-enter', 'cubic-bezier(0.05, 0.7, 0.1, 1)'], ['--ease-exit', 'cubic-bezier(0.3, 0, 0.8, 0.15)'],
  ['--dur-short', '200ms'], ['--dur-medium', '300ms'], ['--dur-long', '500ms'], ['--dur-ring', '900ms'],
]
const copy = {
  en: { sample: 'Nice work. One step today.', btn: 'Start workout', row: "Today's workout", sub: 'Chest and triceps, 45 min', empty: 'Nothing here yet', emptyBody: 'Log your first meal to see it here.' },
  ar: { sample: 'أحسنت. خطوة واحدة اليوم.', btn: 'ابدأ التمرين', row: 'تمرين اليوم', sub: 'الصدر والعضلة ثلاثية الرؤوس، ٤٥ دقيقة', empty: 'لا شيء هنا بعد', emptyBody: 'سجّل وجبتك الأولى لتظهر هنا.' },
}

export function DesignScreen() {
  const [dark, setDark] = useState(() => document.documentElement.dataset.theme === 'dark')
  const [ar, setAr] = useState(false)
  const [replay, setReplay] = useState(0)
  const [ringValue, setRingValue] = useState(0.7)
  const [chip, setChip] = useState<string | null>('a')
  const [seg, setSeg] = useState('x')
  const [on, setOn] = useState(true)
  const [n, setN] = useState(3)
  const [num, setNum] = useState<number | null>(75)
  const [txt, setTxt] = useState('')
  const c = copy[ar ? 'ar' : 'en']

  // The colour variables hang off :root, so the page switches the root theme and puts the old one back on leave.
  useEffect(() => {
    const root = document.documentElement
    const before = root.dataset.theme
    root.dataset.theme = dark ? 'dark' : 'light'
    return () => { if (before) root.dataset.theme = before; else delete root.dataset.theme }
  }, [dark])

  return (
    <div class="ds" dir={ar ? 'rtl' : 'ltr'} lang={ar ? 'ar' : 'en'}>
      <TopBar title="Design catalogue" onBack={goBack} />
      <div class="ds-scroll">
        <div class="ds-bar">
          <Button variant="tonal" onClick={() => setDark(!dark)}>{dark ? 'Theme: dark' : 'Theme: light'}</Button>
          <Button variant="tonal" onClick={() => setAr(!ar)}>{ar ? 'Language: AR (RTL)' : 'Language: EN (LTR)'}</Button>
        </div>
        <p class="muted">{c.sample}</p>

        <Card>
          <h3>Colours (token names)</h3>
          <div class="ds-swatches">
            {colors.map((k) => <div class="ds-sw" key={k}><i style={{ background: `var(--${k})` }} /><span>--{k}</span></div>)}
          </div>
        </Card>

        <Card>
          <h3>Type scale</h3>
          <div class="ds-type">
            {type.map(([name, size, lh, w]) => (
              <div key={name} style={{ fontSize: `${size}px`, lineHeight: `${lh}px`, fontWeight: w }}>{name}<small>{c.sample}</small></div>
            ))}
          </div>
        </Card>

        <Card>
          <h3>Radii</h3>
          <div class="ds-radius">{radii.map(([l, v]) => <div key={v} style={{ borderRadius: `var(${v})` }}>{l}</div>)}</div>
        </Card>

        <Card>
          <h3>Spacing</h3>
          {spaces.map(([l, px]) => <div class="ds-space" key={l}><i style={{ inlineSize: `${px}px` }} />{l}</div>)}
          <p class="muted">gutter 20, card padding 24, card gap 16</p>
        </Card>

        <Card>
          <h3>Motion</h3>
          <div class="ds-stack">
            {motion.map(([k, v]) => <div class="ds-sw" key={k}><span>{k}: {v}</span></div>)}
            <div class="ds-motion">
              <Ring key={replay} value={ringValue} label="Sample ring" size={96}><b>{Math.round(ringValue * 100)}%</b></Ring>
              <Button variant="tonal" onClick={() => setReplay(replay + 1)}>Replay</Button>
              <Button variant="text" onClick={() => setRingValue(ringValue >= 1 ? 0.4 : 1)}>Change value</Button>
            </div>
          </div>
        </Card>

        <SectionHeader title="Buttons" trailing={<Button variant="text">Text</Button>} />
        <Card>
          <div class="ds-stack">
            <Button block icon={<AddIcon size={18} />}>{c.btn}</Button>
            <Button block variant="tonal">Secondary</Button>
            <Button block variant="text">Text button</Button>
            <Button block disabled>Disabled</Button>
          </div>
        </Card>

        <Card>
          <div class="ds-stack">
            <h3>Chips</h3>
            <ChipGroup label="Chips" options={[{ value: 'a', label: 'Chest' }, { value: 'b', label: 'Back' }, { value: 'c', label: 'Legs' }]} value={chip} onChange={setChip} />
            <Chip label="Standalone" />
            <h3>Segmented tabs</h3>
            <Segmented label="Segmented" options={[{ value: 'x', label: 'Day' }, { value: 'y', label: 'Week' }, { value: 'z', label: 'Month' }]} value={seg} onChange={setSeg} />
            <h3>Switch</h3>
            <div class="ds-wrap">
              <button type="button" class="ds-sw-btn" role="switch" aria-checked={on} aria-label="Sample switch" onClick={() => setOn(!on)} />
              <span>{on ? 'On' : 'Off'}</span>
            </div>
            <h3>Stepper</h3>
            <Stepper label="Sets" value={n} onChange={setN} min={1} max={10} />
          </div>
        </Card>

        <Card>
          <div class="ds-stack">
            <h3>List row, stats, progress</h3>
            <ListRow title={c.row} subtitle={c.sub} icon={<AddIcon />} trailing="45" onClick={() => {}} />
            <div class="ds-wrap" style={{ justifyContent: 'space-around' }}>
              <Stat value={1840} label="kcal" accent="blue" />
              <Stat value={132} label="g protein" accent="gold" />
              <Stat value={7} label="days" accent="teal" />
            </div>
            <div class="ds-line" role="progressbar" aria-label="Sample progress" aria-valuemin={0} aria-valuemax={100} aria-valuenow={60}><div style={{ inlineSize: '60%' }} /></div>
            <div class="ds-tile">Tile: md radius, card-high, 16 padding</div>
          </div>
        </Card>

        <Card>
          <h3>Rings</h3>
          <div class="ds-wrap">
            <Ring value={0.66} label="Protein" color="--blue" track="--blue-track"><b>66</b></Ring>
            <Ring value={0.4} label="Carbs" color="--gold" track="--gold-track" size={72}><b>40</b></Ring>
            <Ring value={1} label="Goal" size={72}><b>100</b></Ring>
          </div>
        </Card>

        <Card><EmptyState title={c.empty} body={c.emptyBody} icon={<AddIcon />} action={<Button>{c.btn}</Button>} /></Card>

        <Card>
          <div class="ds-stack">
            <h3>Fields</h3>
            <TextField label="Name" value={txt} onInput={setTxt} hint="Hint text" />
            <TextField label="With error" value="x" onInput={() => {}} error="Something to fix" />
            <NumberField label="Weight" value={num} onChange={setNum} unit="kg" />
          </div>
        </Card>
      </div>
    </div>
  )
}
