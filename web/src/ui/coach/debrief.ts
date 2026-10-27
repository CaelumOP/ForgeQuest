import { t } from '../../i18n'
import { generateDebrief } from '../../data/coach-chat'
import { toast } from '../kit'
import { buzzError } from '../live/feedback'

/**
 * Starts the coach's debrief for a finished workout without waiting for it. It is saved on the session row, which the
 * workout page already shows. Nothing happens without a key or when the setting is off; a real failure is one toast.
 */
export function runDebriefInBackground(sessionId: number): void {
  generateDebrief(sessionId).catch(() => { buzzError(); toast(t('coach_web_debrief_failed')) })
}
