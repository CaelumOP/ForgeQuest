import { useRef, useState } from 'preact/hooks'
import { getLang, t } from '../../i18n'
import { CameraIcon, DumbbellIcon, FireIcon, LeafIcon, SparkleIcon, TrophyIcon } from '../home/glyphs'
import { Button } from '../kit'
import './onboarding.css'

const KEY = 'fq_tour_seen'

export function tourSeen(): boolean {
  try { return localStorage.getItem(KEY) === '1' } catch { return false }
}
export function markTourSeen() {
  try { localStorage.setItem(KEY, '1') } catch { /* shown again next visit */ }
}

type Slide = { Icon: typeof LeafIcon; color: string; n: number }
// Android has a seventh slide (Fitbit readiness); the web has no Fitbit, so it is left out.
const SLIDES: Slide[] = [
  { Icon: LeafIcon, color: '--teal', n: 1 },
  { Icon: FireIcon, color: '--gold', n: 2 },
  { Icon: DumbbellIcon, color: '--teal', n: 3 },
  { Icon: CameraIcon, color: '--coral', n: 4 },
  { Icon: SparkleIcon, color: '--purple', n: 5 },
  { Icon: TrophyIcon, color: '--gold', n: 7 },
]

/** Feature slides with Skip, page dots and Next; the last button reads `finishLabel`. Swipe or tap Next. */
export function Tour({ finishLabel, onDone }: { finishLabel: string; onDone: () => void }) {
  const [page, setPage] = useState(0)
  const track = useRef<HTMLDivElement>(null)
  const last = page === SLIDES.length - 1
  const num = new Intl.NumberFormat(getLang())

  const onScroll = () => {
    const el = track.current
    if (el && el.clientWidth) setPage(Math.min(SLIDES.length - 1, Math.round(Math.abs(el.scrollLeft) / el.clientWidth)))
  }
  const to = (i: number) => {
    const smooth = !matchMedia('(prefers-reduced-motion: reduce)').matches
    track.current?.children[i]?.scrollIntoView({ behavior: smooth ? 'smooth' : 'auto', inline: 'start', block: 'nearest' })
  }

  return (
    <div class="tour" role="dialog" aria-modal="true" aria-label={t('tour_replay')}>
      <div class="tour-top">{!last && <Button variant="text" onClick={onDone}>{t('tour_skip')}</Button>}</div>
      <div class="tour-track" ref={track} onScroll={onScroll}>
        {SLIDES.map(({ Icon, color, n }) => (
          <section class="tour-slide" key={n}>
            <span class="tour-art" style={{ color: `var(${color})`, background: `color-mix(in srgb, var(${color}) 15%, transparent)` }}><Icon size={64} /></span>
            <h1>{t(`tour_${n}_t`)}</h1>
            <p>{t(`tour_${n}_b`)}</p>
          </section>
        ))}
      </div>
      <div class="tour-foot">
        <div class="tour-dots" role="img" aria-label={t('tour_web_page', num.format(page + 1), num.format(SLIDES.length))}>
          {SLIDES.map((_, i) => <i key={i} class={i === page ? 'on' : ''} />)}
        </div>
        <Button onClick={() => (last ? onDone() : to(page + 1))}>{last ? finishLabel : t('tour_next')}</Button>
      </div>
    </div>
  )
}
