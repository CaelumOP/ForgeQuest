import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { plural, t, useLang } from '../../i18n'
import type { ChatRow } from '../../data/db'
import { clearChat, errorKindOf, lastAnswer, planOfMessage, sendCoachMessage, applyChatPlan, takePendingCoachMessage, useChat } from '../../data/coach-chat'
import { updateSettings, useSettings } from '../../data/settings'
import { useLibrary } from '../../data/workouts'
import { Button, Dialog, IconButton, Markdown, Sheet, toast } from '../kit'
import { CalendarIcon, CheckIcon, KeyIcon, MoreIcon, SendIcon } from '../icons'
import { CopyIcon, SparkleIcon } from '../workouts/glyphs'
import { buzzError } from '../live/feedback'
import { PlanPreviewSheet } from '../workouts/PlanEditorScreen'
import './coach.css'

const SUGGESTIONS = ['coach_suggest_week', 'coach_suggest_pull_up', 'coach_suggest_plan', 'coach_suggest_breakfast']

export function CoachScreen(_: { param?: string }) {
  useLang()
  const messages = useChat()
  const settings = useSettings()
  const library = useLibrary()
  const exercises = useMemo(() => new Map(library.map((e) => [e.id, e])), [library])
  const hasKey = settings.apiKey.trim() !== ''
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [menu, setMenu] = useState(false)
  const [confirmClear, setConfirmClear] = useState(false)
  const [preview, setPreview] = useState<ChatRow | null>(null)
  const [stale, setStale] = useState<ChatRow | null>(null)
  const listRef = useRef<HTMLDivElement>(null)

  // A reply that failed buzzes once (not for the history that was already there when the screen opened).
  const lastMsg = useRef<number | null | undefined>(undefined)
  useEffect(() => {
    const m = messages[messages.length - 1]
    if (!m) return
    if (lastMsg.current !== undefined && m.id !== lastMsg.current && m.kind === 'error') buzzError()
    lastMsg.current = m.id ?? null
  }, [messages])
  useEffect(() => {
    const el = listRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [messages.length, sending])

  // "Ask the coach about this workout" on a debrief: sent as the screen opens, no typing.
  useEffect(() => {
    const text = takePendingCoachMessage()
    if (!text) return
    setSending(true)
    void sendCoachMessage(text, true).finally(() => setSending(false))
  }, [])

  const send = async (text: string) => {
    const trimmed = text.trim()
    if (!trimmed || sending || !hasKey) return
    setInput('')
    setSending(true)
    try { await sendCoachMessage(trimmed) } finally { setSending(false) }
  }

  const apply = async (m: ChatRow, force = false) => {
    setPreview(null)
    try {
      const r = await applyChatPlan(m.id!, force)
      if (r === 'APPLIED') toast(t('coach_plan_applied'))
      else if (r === 'STALE') setStale(m)
    } catch { toast(t('live_web_save_failed')) }
  }

  const previewPlan = preview ? planOfMessage(preview) : null
  const showChips = messages.length > 0 && hasKey && !sending
  const last = lastAnswer()

  return (
    <div class="coach">
      <div class="coach-bar">
        <IconButton label={t('cd_more')} onClick={() => setMenu(true)}><MoreIcon /></IconButton>
      </div>
      <div class="coach-list" ref={listRef}>
        {!hasKey && (
          <div class="coach-card">
            <div class="coach-card-head">
              <span class="coach-ic coach-ic-blue"><KeyIcon /></span>
              <span class="coach-card-title">{t('ai_key_needed_coach')}</span>
            </div>
            <Button onClick={() => { location.hash = '#/profile' }}>{t('ai_key_needed_button')}</Button>
          </div>
        )}
        {messages.length === 0 && (
          <div class="coach-welcome">
            <span class="coach-ic coach-ic-big"><SparkleIcon size={32} /></span>
            <h2 class="coach-welcome-title">{t('coach_welcome_title')}</h2>
            <p class="coach-muted">{t('coach_welcome_body')}</p>
            <div class="coach-chips coach-chips-wrap">
              {SUGGESTIONS.map((k) => <button key={k} type="button" class="coach-chip" disabled={!hasKey} onClick={() => send(t(k))}>{t(k)}</button>)}
            </div>
          </div>
        )}
        {messages.map((m) => m.role === 'user'
          ? <div class="coach-row-user" key={m.id}><div class="coach-bubble coach-user">{m.text}</div></div>
          : <CoachMessage key={m.id} m={m} onReview={() => setPreview(m)} />)}
        {sending && <div class="coach-typing" role="status" aria-label={t('coach_web_typing')}><i /><i /><i /></div>}
      </div>
      {showChips && (
        <div class="coach-chips">
          {SUGGESTIONS.slice(0, 3).map((k) => <button key={k} type="button" class="coach-chip" onClick={() => send(t(k))}>{t(k)}</button>)}
        </div>
      )}
      <form class="coach-composer" onSubmit={(e) => { e.preventDefault(); void send(input) }}>
        <textarea
          class="coach-input" rows={1} value={input} disabled={!hasKey} placeholder={t('coach_input_hint')} aria-label={t('coach_input_hint')}
          onInput={(e) => { const el = e.target as HTMLTextAreaElement; setInput(el.value); el.style.blockSize = 'auto'; el.style.blockSize = `${Math.min(el.scrollHeight, 120)}px` }}
        />
        <button type="submit" class="coach-send" aria-label={t('cd_send')} disabled={!input.trim() || sending || !hasKey}><SendIcon /></button>
      </form>

      <Sheet open={menu} onClose={() => setMenu(false)} title={t('coach_model_header')}>
        {last && last.ms >= 0 && <p class="coach-answered">{t('coach_answered_by', last.who, (last.ms / 1000).toFixed(1))}</p>}
        {([
          ['auto', 'coach_model_auto', t('coach_model_auto_hint')],
          ['lite', 'coach_model_lite', [settings.liteModel, t('coach_model_lite_hint')].filter(Boolean).join(' · ')],
          ['flash', 'coach_model_flash', [settings.fastModel, t('coach_model_flash_hint')].filter(Boolean).join(' · ')],
          ['pro', 'coach_model_pro', [settings.smartModel, t('coach_model_pro_hint')].filter(Boolean).join(' · ')],
        ] as const).map(([id, label, hint]) => (
          <button key={id} type="button" class="coach-opt" role="radio" aria-checked={settings.coachModel === id}
            onClick={() => { void updateSettings((s) => ({ ...s, coachModel: id })); setMenu(false) }}>
            <span class="coach-opt-check">{settings.coachModel === id && <CheckIcon size={20} />}</span>
            <span class="coach-opt-text"><span>{t(label)}</span><span class="coach-muted">{hint}</span></span>
          </button>
        ))}
        <Button variant="tonal" block onClick={() => { setMenu(false); setConfirmClear(true) }}>{t('coach_clear')}</Button>
      </Sheet>

      <Dialog
        open={confirmClear} title={t('coach_clear_title')} message={t('coach_clear_body')} confirmLabel={t('action_clear')} destructive
        onCancel={() => setConfirmClear(false)} onConfirm={() => { setConfirmClear(false); void clearChat() }}
      />
      <Dialog
        open={stale != null} title={t('coach_stale_title')} message={t('coach_stale_body')} confirmLabel={t('coach_stale_apply')} destructive
        onCancel={() => setStale(null)} onConfirm={() => { const m = stale!; setStale(null); void apply(m, true) }}
      />
      {preview && previewPlan && (
        <PlanPreviewSheet draft={previewPlan} exercises={exercises} onClose={() => setPreview(null)} onApply={() => apply(preview)} />
      )}
    </div>
  )
}

function CoachMessage({ m, onReview }: { m: ChatRow; onReview: () => void }) {
  const plan = planOfMessage(m)
  const isError = m.kind === 'error'
  const kind = errorKindOf(m)
  const copy = async () => {
    try { await navigator.clipboard.writeText(`${m.text}\n${m.detail}`); toast(t('coach_copied')) } catch { toast(t('workouts_web_copy_failed')) }
  }
  const training = plan ? plan.days.filter((d) => !d.isRest && d.items.length).length : 0
  const exCount = plan ? plan.days.reduce((n, d) => n + d.items.length, 0) : 0
  return (
    <div class="coach-row-coach">
      {m.kind === 'checkin' && <div class="coach-checkin">{t('coach_weekly_checkin')}</div>}
      <div class={`coach-bubble coach-reply${isError ? ' coach-error' : ''}`}>
        <Markdown text={m.text} />
        {(kind === 'no_key' || kind === 'bad_key') && <a class="coach-link" href="#/profile">{t('coach_set_up_profile')}</a>}
      </div>
      {isError && m.detail && (
        <button type="button" class="coach-copy" onClick={copy}><CopyIcon size={16} />{t('coach_copy_details')}</button>
      )}
      {plan && (
        <div class="coach-card coach-plan">
          <div class="coach-card-head">
            <span class="coach-ic"><CalendarIcon /></span>
            <div class="coach-grow">
              <div class="coach-card-title">{plan.name}</div>
              <div class="coach-muted">{t('coach_plan_summary', plural('coach_training_days', training, training), plural('coach_exercises', exCount, exCount))}</div>
            </div>
          </div>
          {m.planApplied
            ? <Button variant="tonal" icon={<CheckIcon size={18} />} onClick={onReview}>{t('coach_applied')}</Button>
            : <Button onClick={onReview}>{t('coach_review_apply')}</Button>}
        </div>
      )}
    </div>
  )
}
