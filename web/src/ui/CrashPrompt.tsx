import { useState } from 'preact/hooks'
import { clearCrash, crashText, loadCrash } from '../data/crash'
import { t } from '../i18n'
import { Button, Card, toast } from './kit'

// Read once per launch: a crash saved during this visit is offered on the next one.
const pending = loadCrash()

/** One-time offer to share the last crash report (share sheet, or the clipboard). Either choice clears it. */
export function CrashPrompt() {
  const [report, setReport] = useState(pending)
  if (!report) return null
  const done = () => { clearCrash(); setReport(null) }
  const send = async () => {
    const text = crashText(report)
    done()
    if (typeof navigator.share === 'function') {
      try { await navigator.share({ title: t('crash_send'), text }) } catch { /* closed the sheet */ }
      return
    }
    try { await navigator.clipboard.writeText(text); toast(t('crash_copied')) } catch { toast(t('workouts_web_copy_failed')) }
  }
  return (
    <div class="crash-prompt" role="status">
      <Card>
        <p class="error-card-title">{t('crash_prompt_title')}</p>
        <p class="muted">{t('crash_prompt_body')}</p>
        <div class="error-card-actions">
          <Button onClick={() => void send()}>{t('crash_send')}</Button>
          <Button variant="text" onClick={done}>{t('crash_dismiss')}</Button>
        </div>
      </Card>
    </div>
  )
}
