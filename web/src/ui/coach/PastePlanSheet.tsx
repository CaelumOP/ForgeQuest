import { useState } from 'preact/hooks'
import { t } from '../../i18n'
import { AiError, aiErrorKey } from '../../data/ai'
import { parsePastedPlan } from '../../data/coach'
import { parsePlanText } from '../../data/coach-chat'
import type { PlanDraft } from '../../data/seed/starter-plans'
import { Button, Sheet, TextField, toast } from '../kit'

/**
 * "Paste plan": a plan that is already JSON (e.g. Gemini's reply to the copied prompt) is read right here; any other
 * text goes to the coach's AI to turn it into a plan. The parsed plan goes to onParsed (the plan preview).
 */
export function PastePlanSheet({ open, onClose, onParsed }: { open: boolean; onClose: () => void; onParsed: (draft: PlanDraft) => void }) {
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const read = async () => {
    const direct = parsePastedPlan(text)
    if (direct) { onParsed(direct); setText(''); return }
    setBusy(true)
    try {
      onParsed(await parsePlanText(text))
      setText('')
    } catch (e) {
      if (e instanceof AiError && e.kind === 'unreadable') toast(t('ai_err_plan_text'))
      else { const k = aiErrorKey(e); toast(t(k.key, ...k.args)) }
    } finally {
      setBusy(false)
    }
  }
  return (
    <Sheet open={open} onClose={onClose} title={t('plan_paste')}>
      <TextField label={t('plan_paste')} multiline value={text} onInput={setText} hint={t('plan_paste_hint')} placeholder={t('plan_paste_hint_text')} />
      <Button block loading={busy} disabled={!text.trim()} onClick={read}>{busy ? t('plan_reading') : t('plan_read')}</Button>
    </Sheet>
  )
}
