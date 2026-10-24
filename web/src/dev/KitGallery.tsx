import { useState } from 'preact/hooks'
import {
  BarChart, Button, Card, Chip, ChipGroup, Dialog, EmptyState, IconButton, LineChart, ListRow, Markdown, NumberField,
  Ring, SectionHeader, Sheet, Stat, Stepper, TextField, ToastHost, toast,
} from '../ui/kit'
import { AddIcon, CheckIcon, TabIcon } from '../ui/icons'

const weights = [82, 81.6, 81.9, 81, 80.4, 80.6, 79.9].map((value, i) => ({ label: `W${i + 1}`, value }))
const days = [3, 5, 0, 4, 6, 2, 5].map((value, i) => ({ label: `D${i + 1}`, value }))
const md = '# Plan\nEat **more protein** and *sleep* well.\n\n- Oats with `whey`\n- Chicken, rice\n\n1. Warm up\n2. Lift\n\nSee [guide](https://example.com) and [bad](javascript:alert(1)). <img src=x onerror=alert(1)>'

/** Dev-only: every kit component in a few states. */
export function KitGallery() {
  const [one, setOne] = useState<string | null>('b')
  const [many, setMany] = useState<string[]>(['a'])
  const [sheet, setSheet] = useState(false)
  const [dlg, setDlg] = useState(false)
  const [n, setN] = useState(3)
  const [num, setNum] = useState<number | null>(75)
  const [txt, setTxt] = useState('')
  const [busy, setBusy] = useState(false)
  const opts = [{ value: 'a', label: 'Chest' }, { value: 'b', label: 'Back' }, { value: 'c', label: 'Legs' }]
  return (
    <div class="content">
      <SectionHeader title="Buttons" trailing={<Button variant="text">Edit</Button>} />
      <div class="chip-demo" style="display:flex;gap:8px;flex-wrap:wrap">
        <Button icon={<AddIcon size={18} />}>Filled</Button>
        <Button variant="tonal">Tonal</Button>
        <Button variant="text">Text</Button>
        <Button loading>Saving</Button>
        <Button disabled>Disabled</Button>
        <Button onClick={() => { setBusy(true); setTimeout(() => setBusy(false), 1200) }} loading={busy}>Tap me</Button>
        <IconButton label="Add" tone="tonal"><AddIcon /></IconButton>
        <IconButton label="Done"><CheckIcon /></IconButton>
      </div>
      <SectionHeader title="Chips" />
      <ChipGroup label="Single" options={opts} value={one} onChange={setOne} />
      <ChipGroup label="Multi" multiple options={opts} value={many} onChange={setMany} accent="purple" />
      <Chip label="Standalone" />
      <Card>
        <SectionHeader title="Card with stats" />
        <div style="display:flex;justify-content:space-around;gap:8px">
          <Stat value="55 g" label="Protein left" accent="teal" />
          <Stat value="125 g" label="Carbs left" accent="purple" />
          <Stat value="1,330" label="Cal left" accent="blue" />
          <Stat value="75" label="Weight (kg)" />
        </div>
      </Card>
      <div style="display:flex;gap:16px;align-items:center">
        <Ring value={0.65} label="Protein" size={96}><b>80</b><small>g</small></Ring>
        <Ring value={0.3} color="--gold" track="--gold-track" label="Streak" size={64} stroke={8} />
        <Ring value={0} label="Empty" size={64} stroke={8} />
      </div>
      <ListRow title="Workouts" subtitle="3 this week" icon={<TabIcon name="fitness" filled={false} />} href="#/workouts" />
      <ListRow title="Backup" subtitle="Last saved today" trailing="12 KB" onClick={() => toast('Saved')} />
      <ListRow title="Static row" subtitle="A very long subtitle that needs to wrap onto a second line on a narrow phone screen" />
      <Card><SectionHeader title="Weight" /><LineChart title="Weight over 7 weeks" data={weights} format={(v) => `${v} kg`} /></Card>
      <Card><SectionHeader title="Sets per day" /><BarChart title="Sets per day" data={days} color="--chart-other" /></Card>
      <Stepper label="Reps" value={n} onChange={setN} min={0} max={30} />
      <TextField label="Name" value={txt} onInput={setTxt} hint="Shown on your profile" />
      <TextField label="Email" value="nope" onInput={() => {}} error="Enter a valid email" />
      <NumberField label="Weight" unit="kg" value={num} onChange={setNum} decimal min={20} max={400} />
      <NumberField label="Age" value={null} onChange={() => {}} error="Required" />
      <Card><Markdown text={md} /></Card>
      <Card><EmptyState title="No workouts yet" body="Start a workout and it will show up here." icon={<TabIcon name="fitness" filled={false} />} action={<Button onClick={() => setSheet(true)}>Open sheet</Button>} /></Card>
      <div style="display:flex;gap:8px">
        <Button variant="tonal" onClick={() => setDlg(true)}>Confirm dialog</Button>
        <Button variant="tonal" onClick={() => toast('Workout saved')}>Toast</Button>
      </div>
      <Sheet open={sheet} onClose={() => setSheet(false)} title="Pick a day">
        <ChipGroup label="Days" options={opts} value={one} onChange={setOne} />
        <Stepper label="Sets" value={n} onChange={setN} />
        <Button block onClick={() => setSheet(false)}>Done</Button>
      </Sheet>
      <Dialog open={dlg} title="Delete workout?" message="This cannot be undone." destructive onConfirm={() => setDlg(false)} onCancel={() => setDlg(false)} />
      <ToastHost />
    </div>
  )
}
