// "Tap to back up": shown above the tab bar when Drive is connected, the sign-in token has run out, and there
// are workouts, meals or weights newer than the last backup. One tap signs in again and backs up.
import { useState } from 'preact/hooks'
import { t } from '../i18n'
import { backupNow, lastActivityAt, tokenForTap, validToken } from '../data/drive'
import { useLive } from '../data/live'
import { useSettings } from '../data/settings'
import { toast } from './kit'
import { driveErrorText } from './profile/BackupSection'

export function BackupPill() {
  const s = useSettings()
  const activity = useLive(lastActivityAt, [], 0)
  const [busy, setBusy] = useState(false)
  if (!s.driveEmail || activity <= s.lastBackupAt || validToken()) return null
  const tap = async () => {
    setBusy(true)
    try { await backupNow(await tokenForTap()); toast(t('drive_backed_up')) } catch (e) { toast(driveErrorText(e)) } finally { setBusy(false) }
  }
  return (
    <button type="button" class="backup-pill" onClick={() => void tap()} disabled={busy}>
      {busy ? t('drive_backing_up') : t('drive_web_tap_backup')}
    </button>
  )
}
