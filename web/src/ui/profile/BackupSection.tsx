// Profile → Backup: Google Drive (same file as Android) and a backup file to save or restore by hand.
import { useEffect, useRef, useState } from 'preact/hooks'
import { plural, t, intlLocale } from '../../i18n'
import { applyBackup, backupCounts, buildBackup, decodeBackup, encodeBackup, listSafetyCopies, type BackupFile } from '../../data/backup'
import { connectDrive, disconnectDrive, DriveError, backupNow, fetchDriveBackup, preloadGis, tokenForTap } from '../../data/drive'
import { useLive } from '../../data/live'
import type { Settings } from '../../data/settings'
import { Button, Card, Dialog, toast } from '../kit'

const when = (ms: number) => new Intl.DateTimeFormat(intlLocale(), { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }).format(ms)

/** Group summary line, like Android's. */
export const backupSummary = (s: Settings) =>
  !s.driveEmail ? t('summary_drive_off') : s.lastBackupAt > 0 ? t('summary_drive_last', when(s.lastBackupAt)) : t('summary_drive_none')

export function driveErrorText(e: unknown): string {
  if (e instanceof DriveError) {
    if (e.kind === 'offline') return t('drive_err_offline')
    if (e.kind === 'cancelled') return t('drive_web_signin_cancelled')
    if (e.kind === 'no-backup') return t('drive_err_no_backup')
    return t('drive_err_http', e.status)
  }
  return t('backup_restore_failed')
}

type Pending = { file: BackupFile; source: string }

const contents = (f: BackupFile) => {
  const c = backupCounts(f)
  return [plural('backup_workouts', c.workouts), plural('backup_meals', c.meals), plural('backup_weights', c.weights)].join(' · ')
}

export function BackupSection({ s, Title }: { s: Settings; Title: (p: { title: string; hint?: string }) => preact.JSX.Element }) {
  const [busy, setBusy] = useState<string | null>(null)
  const [pending, setPending] = useState<Pending | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)
  const copies = useLive(listSafetyCopies, [], [])
  useEffect(() => { void preloadGis().catch(() => undefined) }, [])

  const run = async (label: string, job: () => Promise<void>) => {
    if (busy) return
    setBusy(label)
    try { await job() } catch (e) { console.warn('backup action failed', e); toast(driveErrorText(e)) } finally { setBusy(null) }
  }

  const connect = () => run(t('drive_connecting'), async () => {
    const r = await connectDrive()
    if (r.kind === 'offer-restore') setPending({ file: r.file, source: t('restore_src_drive') })
    else toast(t('drive_connected_backed_up'))
  })
  const backUp = () => run(t('drive_backing_up'), async () => {
    toast((await backupNow(await tokenForTap())) ? t('drive_backed_up') : t('drive_web_nothing_to_back_up'))
  })
  const fromDrive = () => run(t('drive_reading_backup'), async () => {
    setPending({ file: await fetchDriveBackup(await tokenForTap()), source: t('restore_src_drive') })
  })
  const saveFile = () => run(t('backup_preparing'), async () => {
    const day = new Date().toISOString().slice(0, 10)
    const file = new File([encodeBackup(await buildBackup())], `forgequest-backup-${day}.json`, { type: 'application/json' })
    if (navigator.canShare?.({ files: [file] })) {
      try { await navigator.share({ files: [file], title: t('backup_share_title') }) } catch { return } // closed the sheet
    } else {
      const a = document.createElement('a')
      a.href = URL.createObjectURL(file)
      a.download = file.name
      a.click()
      setTimeout(() => URL.revokeObjectURL(a.href), 10_000)
    }
    toast(t('backup_saved'))
  })
  const fromFile = (f: File | undefined) => f && run(t('backup_reading_file'), async () => {
    try { setPending({ file: decodeBackup(await f.text()), source: t('restore_src_file') }) } catch { toast(t('backup_err_unreadable')) }
  })
  const undo = () => run(t('backup_reading_copy'), async () => {
    const latest = copies[0]
    if (latest) setPending({ file: decodeBackup(latest.json), source: t('restore_src_copy') })
  })
  const restore = () => {
    const p = pending
    setPending(null)
    if (p) void run(t('backup_restoring'), async () => { await applyBackup(p.file); toast(t('backup_restored_from', p.source)) })
  }

  return (
    <>
      <Card>
        <Title title={t('drive_title')} hint={s.driveEmail ? `${s.driveEmail}` : t('drive_web_hint')} />
        {s.driveEmail && <span class="pf-s">{backupSummary(s)}</span>}
        {busy && <span class="pf-s" role="status">{busy}</span>}
        <div class="pf-actions">
          {!s.driveEmail
            ? <Button onClick={connect} loading={busy === t('drive_connecting')}>{t('drive_connect')}</Button>
            : <>
                <Button variant="tonal" onClick={backUp} disabled={!!busy}>{t('drive_back_up_now')}</Button>
                <Button variant="tonal" onClick={fromDrive} disabled={!!busy}>{t('drive_web_restore')}</Button>
                <Button variant="text" onClick={() => void disconnectDrive()} disabled={!!busy}>{t('drive_disconnect')}</Button>
              </>}
        </div>
      </Card>
      <Card>
        <Title title={t('backup_web_file_title')} hint={t('backup_web_file_hint')} />
        <div class="pf-actions">
          <Button variant="tonal" onClick={saveFile} disabled={!!busy}>{t('backup_web_save_file')}</Button>
          <Button variant="tonal" onClick={() => fileInput.current?.click()} disabled={!!busy}>{t('backup_web_restore_file')}</Button>
          {copies.length > 0 && <Button variant="text" onClick={undo} disabled={!!busy}>{t('profile_undo_restore')}</Button>}
        </div>
        <input ref={fileInput} type="file" accept=".json,application/json" hidden
          onChange={(e) => { const el = e.target as HTMLInputElement; fromFile(el.files?.[0]); el.value = '' }} />
      </Card>
      <Dialog open={pending != null} title={pending ? t('restore_title', pending.source) : ''}
        message={pending ? `${contents(pending.file)}\n\n${t('restore_body')}` : undefined}
        confirmLabel={t('drive_web_restore_confirm')} destructive onConfirm={restore} onCancel={() => setPending(null)} />
    </>
  )
}
