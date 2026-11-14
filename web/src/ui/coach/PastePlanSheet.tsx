import { useState } from 'preact/hooks'
import { t } from '../../i18n'
import { parsePastedPlan } from '../../data/coach'
import type { PlanDraft } from '../../data/seed/starter-plans'
import { Button, Sheet, TextField, toast } from '../kit'

/**
 * "Paste plan": reads a JSON plan (e.g. from Gemini or Claude) into `onParsed` (the plan preview).
 */
export function PastePlanSheet({ open, onClose, onParsed }: { open: boolean; onClose: () => void; onParsed: (draft: PlanDraft) => void }) {
  const [text, setText] = useState('')
  const read = () => {
    const direct = parsePastedPlan(text)
    if (direct) { onParsed(direct); setText(''); return }
    toast(t('ai_err_plan_text'))
  }
  return (
    <Sheet open={open} onClose={onClose} title={t('plan_paste')}>
      <TextField label={t('plan_paste')} multiline value={text} onInput={setText} hint={t('plan_paste_hint')} placeholder={t('plan_paste_hint_text')} />
      <Button block disabled={!text.trim()} onClick={read}>{t('plan_read')}</Button>
    </Sheet>
  )
}
