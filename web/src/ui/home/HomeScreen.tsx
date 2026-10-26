import { useState } from 'preact/hooks'
import { getLang, plural, t } from '../../i18n'
import { logWeight, setDayTag, useProgress, type ProgressSnapshot } from '../../data/progress'
import { planDay, startSession, useOpenSession } from '../../data/workouts'
import { weightUnit } from '../../data/settings'
import { dayOfWeek, todayEpochDay, weekStart } from '../../domain/dates'
import { DAY_TAGS, type DayState, type DayTag } from '../../domain/progress'
import { weeklyCounts } from '../../domain/trends'
import { bodyStep, fromKg, toKg, unitLabel, weightNumber } from '../../domain/units'
import { BADGES } from '../../domain/badges'
import { gramsLeft } from '../../domain/nutrition'
import { Button, Card, Chip, Ring, Sheet, Stat, Stepper } from '../kit'
import {
  CheckCircleIcon, DumbbellIcon, EditIcon, FireIcon, MoonIcon, PauseIcon, TickIcon, TrophyIcon,
} from './glyphs'
import { canExcuse, greetingKey } from './helpers'
import { RaadEgg } from './RaadEgg'
import { CalorieCard } from './CalorieCard'
import './home.css'

const nf = () => new Intl.NumberFormat(getLang())
const go = (hash: string) => { location.hash = hash }
const dayDate = (epochDay: number) => new Date(epochDay * 86_400_000)

export function HomeScreen(_: { param?: string }) {
  const s = useProgress()
  const open = useOpenSession()
  const [weightOpen, setWeightOpen] = useState(false)
  const [tagDay, setTagDay] = useState<number | null>(null)
  if (!s) return <div class="content home" />
  const today = todayEpochDay()
  const plan = s.plan
  const todayPlan = plan ? planDay(plan, dayOfWeek(today)) ?? null : null
  const unseen = s.badges.filter((b) => !b.seen).map((b) => BADGES.find((d) => d.id === b.id)).filter((d) => d != null)

  const start = async () => {
    await startSession(todayPlan && !todayPlan.day.isRest ? todayPlan : null)
    go('#/live')
  }

  return (
    <div class="content home">
      <Header s={s} />
      <Rings s={s} />
      <StatRow s={s} onWeight={() => setWeightOpen(true)} />
      {unseen.length > 0 && (
        <Card href="#/profile">
          <div class="home-row">
            <span class="home-ic" style={{ background: 'var(--gold-track)', color: 'var(--gold)' }}><TrophyIcon /></span>
            <div class="home-grow">
              <div class="home-t">{plural('home_new_badges', unseen.length)}</div>
              <div class="home-s home-clip">{unseen.map((d) => badgeTitle(d.id, d.title)).join(' · ')}</div>
            </div>
          </div>
        </Card>
      )}
      <CalorieCard s={s} today={today} />
      <Streak s={s} onDay={setTagDay} />
      <ProgressCard s={s} today={today} />
      <TodayCard
        s={s} title={todayPlan?.day.title ?? ''} isRest={!todayPlan || todayPlan.day.isRest} noPlan={!todayPlan}
        note={todayPlan?.day.note ?? ''}
        names={[...new Set((todayPlan?.items ?? []).map((i) => s.exercises.get(i.exerciseId)?.name).filter((n): n is string => !!n))]}
        hasOpen={!!open} onStart={start} onCant={() => setTagDay(today)}
      />
      <WeightSheet s={s} open={weightOpen} onClose={() => setWeightOpen(false)} />
      <DayTagSheet day={tagDay} current={tagDay != null ? s.dayTags.get(tagDay) ?? null : null} onClose={() => setTagDay(null)} />
      <RaadEgg name={s.settings.name} />
    </div>
  )
}

function badgeTitle(id: string, fallback: string): string {
  const key = `badge_${id}_title`
  const v = t(key)
  return v === key ? fallback : v
}

function Header({ s }: { s: ProgressSnapshot }) {
  const greeting = t(greetingKey(new Date().getHours()))
  const name = s.settings.name.trim()
  const date = new Intl.DateTimeFormat(getLang(), { weekday: 'long', month: 'short', day: 'numeric' }).format(new Date())
  return (
    <div class="home-head">
      <div class="home-head-text">
        <h2 class="home-greet">{name ? t('home_greeting_name', greeting, name) : greeting}</h2>
        <div class="home-date">{date}</div>
      </div>
      <a class="home-rank" href="#/progress" aria-label={t('home_web_rank_cd', s.level.rank.letter)}>
        <Ring value={s.level.progress} color="--blue" track="--blue-track" size={44} stroke={3} label={s.level.rank.letter}>
          <span class="home-rank-letter">{s.level.rank.letter}</span>
        </Ring>
      </a>
    </div>
  )
}

function Arc({ value, color, track, size, stroke, inset }: { value: number; color: string; track: string; size: number; stroke: number; inset: number }) {
  const v = Math.min(1, Math.max(0, Number.isFinite(value) ? value : 0))
  const r = (size - stroke) / 2 - inset
  const c = 2 * Math.PI * r
  return (
    <g transform={`rotate(-90 ${size / 2} ${size / 2})`}>
      <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={`var(${track})`} stroke-width={stroke} />
      {v > 0 && <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={`var(${color})`} stroke-width={stroke} stroke-linecap="round" stroke-dasharray={`${c * v} ${c}`} />}
    </g>
  )
}

function Rings({ s }: { s: ProgressSnapshot }) {
  const { settings: st, today: n } = s
  const goalKcal = Math.max(st.kcalGoal, 1)
  const goalProtein = Math.max(st.proteinGoal, 1)
  const over = n.kcal > goalKcal
  const kcalColor = over ? '--gold' : '--blue'
  const kcalTrack = over ? '--gold-track' : '--blue-track'
  const carbs = st.trackCarbs
  const size = 210
  const stroke = carbs ? 13 : 16
  const gap = stroke + 4
  const specs = [
    { value: n.protein / goalProtein, color: '--teal', track: '--teal-track' },
    ...(carbs ? [{ value: n.carbs / Math.max(st.carbsGoal, 1), color: '--purple', track: '--purple-track' }] : []),
    { value: n.kcal / goalKcal, color: kcalColor, track: kcalTrack },
  ]
  return (
    <a class="home-rings" href="#/food" aria-label={t('home_web_rings_cd', Math.round(n.protein), st.proteinGoal, nf().format(n.kcal), nf().format(st.kcalGoal))}>
      <div class="home-rings-box" style={{ inlineSize: `${size}px`, blockSize: `${size}px` }}>
        <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden="true" focusable="false">
          {specs.map((p, i) => <Arc key={i} {...p} size={size} stroke={stroke} inset={i * gap} />)}
        </svg>
        <div class="home-rings-center">
          <div class="home-rings-protein">{Math.round(n.protein)}</div>
          <div class="home-rings-cap">{t('home_center_protein')}</div>
          <div class="home-rings-kcal" style={{ color: `var(${kcalColor})` }}>{t('home_center_kcal', nf().format(n.kcal))}</div>
        </div>
      </div>
      <div class="home-legend">
        <span><i class="home-dot" style={{ background: 'var(--teal)' }} />{t('home_legend_protein')}</span>
        {carbs && <span><i class="home-dot" style={{ background: 'var(--purple)' }} />{t('label_carbs')}</span>}
        <span><i class="home-dot" style={{ background: `var(${kcalColor})` }} />{t('home_legend_calories')}</span>
      </div>
    </a>
  )
}

function StatRow({ s, onWeight }: { s: ProgressSnapshot; onWeight: () => void }) {
  const { settings: st, today: n } = s
  const proteinLeft = gramsLeft(st.proteinGoal, n.protein)
  const kcalLeft = st.kcalGoal - n.kcal
  const unit = weightUnit(st)
  const ar = getLang() === 'ar'
  return (
    <div class="home-stats">
      <Stat value={proteinLeft > 0 ? t('home_protein_left_value', proteinLeft) : t('home_done')} label={proteinLeft > 0 ? t('home_protein_left') : t('home_protein_goal')} accent="teal" />
      {st.trackCarbs && <Stat value={t('home_protein_left_value', Math.max(0, gramsLeft(st.carbsGoal, n.carbs)))} label={t('home_carbs_left')} accent="purple" />}
      <Stat value={nf().format(Math.abs(kcalLeft))} label={kcalLeft >= 0 ? t('home_cal_left') : t('home_cal_surplus')} accent={kcalLeft >= 0 ? 'blue' : 'gold'} />
      <button type="button" class="home-statbtn" aria-label={t('home_web_weight_cd')} onClick={onWeight}>
        <Stat value={s.latestWeight != null ? weightNumber(unit, s.latestWeight) : '–'} label={t('home_weight_unit', unitLabel(unit, ar))} accent="text" />
      </button>
    </div>
  )
}

function ProgressCard({ s, today }: { s: ProgressSnapshot; today: number }) {
  if (!s.sessions.length) return null
  const weekly = weeklyCounts(s.sessions.map((x) => x.epochDay), 8, today)
  const workouts = s.sessions.filter((x) => x.epochDay > today - 30).length
  const days = new Map(s.sessions.map((x) => [x.id, x.epochDay]))
  const records = s.doneSets.filter((x) => x.isPr && (days.get(x.sessionId) ?? 0) > today - 30).length
  return (
    <Card href="#/progress">
      <div class="home-row">
        <div class="home-grow">
          <div class="home-label">{t('home_progress_card')}</div>
          <div class="home-t">{plural('home_month_workouts', workouts)} · {plural('home_month_records', records)}</div>
          <div class="home-link">{t('home_progress_open')}</div>
        </div>
        <Sparkline values={weekly} />
      </div>
    </Card>
  )
}

function Sparkline({ values }: { values: number[] }) {
  const w = 110, h = 40, max = Math.max(1, ...values)
  const pts = values.map((v, i) => `${(i / Math.max(1, values.length - 1)) * w},${h - 4 - (v / max) * (h - 8)}`).join(' ')
  return (
    <svg class="home-spark" width={w} height={h} viewBox={`0 0 ${w} ${h}`} aria-hidden="true" focusable="false">
      <polyline points={pts} fill="none" stroke="var(--chart-strength)" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" />
    </svg>
  )
}

function DayCircle({ state }: { state: DayState }) {
  switch (state) {
    case 'DONE': return <span class="home-circle done" role="img" aria-label={t('cd_done')}><TickIcon size={20} /></span>
    case 'TODAY_PLANNED': return <span class="home-circle ring today"><i class="home-pip" /></span>
    case 'UPCOMING': return <span class="home-circle ring" />
    case 'MISSED': return <span class="home-circle ring"><i class="home-pip sm" /></span>
    case 'REST': return <span class="home-circle rest" role="img" aria-label={t('cd_rest')}><MoonIcon size={16} /></span>
    case 'EXCUSED': return <span class="home-circle excused" role="img" aria-label={t('cd_excused')}><PauseIcon size={16} /></span>
    default: return <span class="home-circle off" />
  }
}

function Streak({ s, onDay }: { s: ProgressSnapshot; onDay: (d: number) => void }) {
  const k = s.streak
  const today = todayEpochDay()
  const start = weekStart(today)
  const alive = k.weekProgress > 0 || k.weeks > 0
  const status = k.freeWeek ? t('streak_starts_next_week') : k.hitThisWeek ? t('streak_week_complete') : t('streak_done_of_target', k.doneThisWeek, k.targetThisWeek)
  const sub = k.freeWeek ? t('streak_first_week')
    : k.weeks === 0 && k.doneThisWeek === 0 ? t('streak_hit_to_start')
    : k.hitThisWeek ? plural('streak_best', k.best)
    : plural('streak_more_to_go', k.targetThisWeek - k.doneThisWeek)
  const fmt = new Intl.DateTimeFormat(getLang(), { weekday: 'narrow', timeZone: 'UTC' })
  const long = new Intl.DateTimeFormat(getLang(), { weekday: 'long', timeZone: 'UTC' })
  return (
    <Card>
      <div class="home-streak-top">
        <h2 class="home-t">{t('streak_title')}</h2>
        <span class="home-label" style={{ color: k.hitThisWeek ? 'var(--gold)' : undefined }}>{status}</span>
      </div>
      <div class="home-row">
        <Ring value={k.weekProgress} color="--gold" track="--gold-track" size={56} stroke={4} label={t('streak_title')}>
          <span style={{ color: alive ? 'var(--gold)' : 'var(--text-2)', opacity: alive ? 0.45 + 0.55 * Math.max(k.weekProgress, k.weeks > 0 ? 0.4 : 0) : 0.5, display: 'inline-flex' }}><FireIcon size={28} /></span>
        </Ring>
        <div class="home-grow">
          <div class="home-big">{plural('streak_weeks', k.weeks)}</div>
          <div class="home-s">{sub}</div>
        </div>
      </div>
      <div class="home-week">
        {k.days.map((state, i) => {
          const day = start + i
          const letter = fmt.format(dayDate(day))
          const inner = <><DayCircle state={state} /><small>{letter}</small></>
          return canExcuse(day, today, state)
            ? <button type="button" key={i} class="home-day" aria-label={`${t('daytag_title')}: ${long.format(dayDate(day))}`} onClick={() => onDay(day)}>{inner}</button>
            : <span key={i} class="home-day">{inner}</span>
        })}
      </div>
    </Card>
  )
}

function TodayCard(p: {
  s: ProgressSnapshot; title: string; isRest: boolean; noPlan: boolean; note: string; names: string[]
  hasOpen: boolean; onStart: () => void; onCant: () => void
}) {
  const { s, isRest, noPlan, hasOpen } = p
  const done = s.workoutDoneToday
  const excused = s.excusedToday
  const head = hasOpen ? t('today_in_progress') : noPlan ? t('today_no_plan') : isRest ? t('today_rest_day') : p.title
  const sub = hasOpen ? t('today_resume_hint')
    : done ? t('today_done')
    : excused ? t('today_excused', t(`daytag_${excused.toLowerCase()}`))
    : noPlan ? t('today_no_plan_hint')
    : isRest ? (p.note.trim() || t('today_rest_hint'))
    : plural('today_exercises', p.names.length, p.names.length, p.names.slice(0, 3).join(t('list_separator')))
  const tone = noPlan && !hasOpen ? ['--teal', EditIcon] : isRest && !hasOpen ? ['--blue', MoonIcon] : ['--teal', DumbbellIcon]
  const Icon = tone[1] as typeof EditIcon
  const colour = tone[0] as string
  return (
    <Card>
      <div class="home-label">{t('today_title')}</div>
      <div class="home-row home-today">
        <a class="home-row home-grow home-plan-link" href="#/workouts">
          <span class="home-ic" style={{ background: `var(${colour}-track)`, color: `var(${colour})` }}><Icon /></span>
          <span class="home-grow">
            <span class="home-t home-clip" style={{ display: 'block' }}>{head}</span>
            <span class="home-s home-sub2" style={{ display: 'block' }}>{sub}</span>
          </span>
        </a>
        {hasOpen ? <Button onClick={p.onStart}>{t('action_resume')}</Button>
          : done ? <span style={{ color: 'var(--teal)' }}><CheckCircleIcon /></span>
          : noPlan ? <Button onClick={() => go('#/workouts')}>{t('action_set_up')}</Button>
          : !isRest ? <Button onClick={p.onStart}>{t('action_start')}</Button> : null}
      </div>
      {!isRest && !hasOpen && !done && !excused && (
        <Button variant="text" class="home-cant" onClick={p.onCant}>{t('today_cant_train')}</Button>
      )}
    </Card>
  )
}

function WeightSheet({ s, open, onClose }: { s: ProgressSnapshot; open: boolean; onClose: () => void }) {
  const unit = weightUnit(s.settings)
  const ar = getLang() === 'ar'
  const [kg, setKg] = useState<number | null>(null)
  const cur = kg ?? s.latestWeight ?? 75
  const shown = (n: number) => `${n.toFixed(1)} ${unitLabel(unit, ar)}`
  const close = () => { setKg(null); onClose() }
  return (
    <Sheet open={open} onClose={close} title={t('weight_log_title')}>
      <div class="home-sheet">
        <Stepper label={t('weight_body')} value={Math.round(fromKg(unit, cur) * 10) / 10} step={bodyStep(unit)} min={fromKg(unit, 30)} max={fromKg(unit, 250)}
          onChange={(v) => setKg(toKg(unit, v))} format={shown} />
        <Button block onClick={async () => { await logWeight(cur); close() }}>{t('action_save')}</Button>
      </div>
    </Sheet>
  )
}

function DayTagSheet({ day, current, onClose }: { day: number | null; current: DayTag | null; onClose: () => void }) {
  const name = day != null ? new Intl.DateTimeFormat(getLang(), { weekday: 'long', timeZone: 'UTC' }).format(dayDate(day)) : ''
  const pick = async (tag: DayTag | null) => { if (day != null) await setDayTag(day, tag); onClose() }
  return (
    <Sheet open={day != null} onClose={onClose} title={t('daytag_title')}>
      <p class="home-s" style={{ margin: 0 }}>{t('daytag_body', name)}</p>
      <div class="home-actions" role="radiogroup" aria-label={t('daytag_title')}>
        {DAY_TAGS.map((tag) => (
          <Chip key={tag} role="radio" label={t(`daytag_${tag.toLowerCase()}`)} selected={current === tag} onClick={() => pick(tag)} />
        ))}
      </div>
      <div class="home-sheet-actions">
        <Button variant="text" onClick={onClose}>{t('action_cancel')}</Button>
        {current && <Button variant="text" onClick={() => pick(null)}>{t('daytag_clear')}</Button>}
      </div>
    </Sheet>
  )
}
