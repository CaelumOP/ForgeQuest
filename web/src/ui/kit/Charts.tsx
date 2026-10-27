import { useId } from 'preact/hooks'
import { t } from '../../i18n'

export type Point = { label: string; value: number }
type ChartBase = {
  data: Point[]
  /** Accessible title, also the table caption. */
  title: string
  /** CSS variable name for the series, e.g. '--chart-weight'. */
  color?: string
  height?: number
  /** Formats values for the screen reader table and the end labels. */
  format?: (n: number) => string
}

const W = 320
const PAD = { t: 12, b: 24, s: 8 }

function Table({ title, data, format }: { title: string; data: Point[]; format: (n: number) => string }) {
  return (
    <table class="kit-sr">
      <caption>{title}</caption>
      <tbody>
        {data.map((d, i) => (
          <tr key={i}><th scope="row">{d.label}</th><td>{format(d.value)}</td></tr>
        ))}
      </tbody>
    </table>
  )
}

/** One sentence a screen reader says for the whole chart: first and last points and the best one. */
function summary(data: Point[], format: (n: number) => string): string {
  if (data.length < 2) return data.length ? `${data[0].label} ${format(data[0].value)}` : ''
  const first = data[0], last = data[data.length - 1]
  const best = data.reduce((a, b) => (b.value > a.value ? b : a))
  return t('a11y_chart_summary', data.length, format(first.value), first.label, format(last.value), last.label, format(best.value))
}

function scale(data: Point[], zeroBase: boolean) {
  const vals = data.map((d) => d.value)
  let lo = zeroBase ? 0 : Math.min(...vals)
  let hi = Math.max(...vals)
  if (hi === lo) { hi += 1; if (!zeroBase) lo -= 1 }
  return { lo, hi }
}

export function LineChart({ data, title, color = '--chart-weight', height = 160, format = String }: ChartBase) {
  const id = useId()
  if (!data.length) return null
  const { lo, hi } = scale(data, false)
  const h = height
  const x = (i: number) => PAD.s + (data.length === 1 ? (W - 2 * PAD.s) / 2 : (i * (W - 2 * PAD.s)) / (data.length - 1))
  const y = (v: number) => PAD.t + (1 - (v - lo) / (hi - lo)) * (h - PAD.t - PAD.b)
  const pts = data.map((d, i) => `${x(i).toFixed(1)},${y(d.value).toFixed(1)}`)
  return (
    <figure class="kit-chart">
      <svg class="kit-chart-svg" viewBox={`0 0 ${W} ${h}`} role="img" aria-labelledby={id} aria-describedby={`${id}-d`}>
        <title id={id}>{title}</title>
        <desc id={`${id}-d`}>{summary(data, format)}</desc>
        <line x1={PAD.s} x2={W - PAD.s} y1={h - PAD.b} y2={h - PAD.b} stroke="var(--divider)" stroke-width="1" />
        <polyline points={pts.join(' ')} fill="none" stroke={`var(${color})`} stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round" />
        {data.map((d, i) => <circle key={i} cx={x(i)} cy={y(d.value)} r="3.5" fill={`var(${color})`} />)}
      </svg>
      <div class="kit-chart-axis" aria-hidden="true">
        <span>{data[0].label}</span><span>{format(data[data.length - 1].value)}</span><span>{data[data.length - 1].label}</span>
      </div>
      <Table title={title} data={data} format={format} />
    </figure>
  )
}

export function BarChart({ data, title, color = '--chart-strength', height = 160, format = String }: ChartBase) {
  const id = useId()
  if (!data.length) return null
  const { hi } = scale(data, true)
  const h = height
  const slot = (W - 2 * PAD.s) / data.length
  const bw = Math.min(28, slot * 0.6)
  return (
    <figure class="kit-chart">
      <svg class="kit-chart-svg" viewBox={`0 0 ${W} ${h}`} role="img" aria-labelledby={id} aria-describedby={`${id}-d`}>
        <title id={id}>{title}</title>
        <desc id={`${id}-d`}>{summary(data, format)}</desc>
        <line x1={PAD.s} x2={W - PAD.s} y1={h - PAD.b} y2={h - PAD.b} stroke="var(--divider)" stroke-width="1" />
        {data.map((d, i) => {
          const bh = Math.max(d.value > 0 ? 2 : 0, (d.value / hi) * (h - PAD.t - PAD.b))
          return <rect key={i} x={PAD.s + i * slot + (slot - bw) / 2} y={h - PAD.b - bh} width={bw} height={bh} rx="4" fill={`var(${color})`} />
        })}
      </svg>
      <div class="kit-chart-axis" aria-hidden="true">
        <span>{data[0].label}</span><span>{format(data[data.length - 1].value)}</span><span>{data[data.length - 1].label}</span>
      </div>
      <Table title={title} data={data} format={format} />
    </figure>
  )
}
