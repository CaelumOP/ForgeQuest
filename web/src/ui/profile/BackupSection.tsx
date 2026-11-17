// Profile → Backup: Google Drive (same file as Android) and a backup file to save or restore by hand.
import { useEffect, useRef, useState } from 'preact/hooks'
import { plural, t, intlLocale } from '../../i18n'
import { backupCounts, buildBackup, decodeBackup, encodeBackup, listSafetyCopies, type BackupFile } from '../../data/backup'
import { requestPersistence } from '../../data/db'
import {
  backupNow, connectDrive, disconnectDrive, DriveError, fetchDriveBackupPreview, preloadGis, restoreSerialized, tokenForTap, validToken,
  type DriveRestoreMeta,
} from '../../data/drive'
import { useLive } from '../../data/live'
import { autoBackupEffective, setAutoBackup, type Settings } from '../../data/settings'
import { Button, Card, Dialog, ListRow, toast } from '../kit'

const when = (ms: number) => new Intl.DateTimeFormat(intlLocale(), { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }).format(ms)

/** Group summary line, like Android's. */
export const backupSummary = (s: Settings) =>
  !s.driveEmail ? t('summary_drive_off') : s.lastBackupAt > 0 ? t('summary_drive_last', when(s.lastBackupAt)) : t('summary_drive_none')

export function driveErrorText(e: unknown): string {
  if (e instanceof DriveError) {
    if (e.kind === 'offline') return t('drive_err_offline')
    if (e.kind === 'cancelled') return t('drive_web_signin_cancelled')
    if (e.kind === 'no-backup') return t('drive_err_no_backup')
    if (e.kind === 'remote-newer') return t('prog41_drive_remote_newer_title')
    return t('drive_err_http', e.status)
  }
  return t('backup_restore_failed')
}

type Pending = { file: BackupFile; source: string; isAutoOffer?: boolean; driveMeta?: DriveRestoreMeta }

const contents = (f: BackupFile) => {
  const c = backupCounts(f)
  return [plural('backup_workouts', c.workouts), plural('backup_meals', c.meals), plural('backup_weights', c.weights)].join(' · ')
}

export function BackupSection({ s, Title }: { s: Settings; Title: (p: { title: string; hint?: string }) => preact.JSX.Element }) {
  const [busy, setBusy] = useState<string | null>(null)
  const [pending, setPending] = useState<Pending | null>(null)
  const [noticeOpen, setNoticeOpen] = useState(false)
  const [conflictKind, setConflictKind] = useState<'exists' | 'newer' | null>(null)
  const [confirmKeepMine, setConfirmKeepMine] = useState(false)
  const [online, setOnline] = useState(() => (typeof navigator !== 'undefined' ? navigator.onLine : true))
  const [persisted, setPersisted] = useState<boolean | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)
  const copies = useLive(listSafetyCopies, [], [])

  useEffect(() => {
    void preloadGis().catch(() => undefined)
    void navigator.storage?.persisted?.().then(setPersisted).catch(() => setPersisted(false))
    const onUp = () => setOnline(true)
    const onDown = () => setOnline(false)
    addEventListener('online', onUp)
    addEventListener('offline', onDown)
    return () => { removeEventListener('online', onUp); removeEventListener('offline', onDown) }
  }, [])

  const run = async (label: string, job: () => Promise<void>) => {
    if (busy) return
    setBusy(label)
    try {
      await job()
    } catch (e) {
      if (e instanceof DriveError && e.kind === 'remote-newer') {
        setConflictKind('newer')
      } else {
        console.warn('backup action failed', e)
        toast(driveErrorText(e))
      }
    } finally {
      setBusy(null)
    }
  }

  const connect = () => run(t('drive_connecting'), async () => {
    const r = await connectDrive()
    if (r.kind === 'offer-restore') {
      setPending({ file: r.file, source: t('restore_src_drive'), isAutoOffer: true, driveMeta: { accountKey: r.accountKey, modifiedTime: r.modifiedTime } })
    } else if (r.kind === 'conflict') {
      setConflictKind('exists')
    } else if (!r.autoBackupOn) {
      toast(t('prog41_drive_connected_auto_off'))
    } else {
      toast(r.backedUp ? t('drive_connected_backed_up') : t('drive_web_nothing_to_back_up'))
    }
  })

  const backUp = (force = false) => run(t('drive_backing_up'), async () => {
    toast((await backupNow(await tokenForTap(), force)) ? t('drive_backed_up') : t('drive_web_nothing_to_back_up'))
  })

  const fromDrive = () => run(t('drive_reading_backup'), async () => {
    const preview = await fetchDriveBackupPreview(await tokenForTap())
    setPending({ file: preview.file, source: t('restore_src_drive'), driveMeta: { accountKey: preview.accountKey, modifiedTime: preview.modifiedTime } })
  })

  const saveFile = () => run(t('backup_preparing'), async () => {
    const day = new Date().toISOString().slice(0, 10)
    const file = new File([encodeBackup(await buildBackup())], `forgequest-backup-${day}.json`, { type: 'application/json' })
    if (navigator.canShare?.({ files: [file] })) {
      try { await navigator.share({ files: [file], title: t('backup_share_title') }) } catch { return }
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
    if (p) void run(t('backup_restoring'), async () => { await restoreSerialized(p.file, p.driveMeta); toast(t('backup_restored_from', p.source)) })
  }

  const protectStorage = async () => {
    const ok = await requestPersistence()
    setPersisted(ok)
  }

  const autoOn = s.autoBackup && !s.autoBackupOptedOut
  const needsReconnect = autoBackupEffective(s) && !validToken()

  return (
    <>
      <Card padded={false}>
        <ListRow title={t('prog41_csv_import_title')} subtitle={t('prog41_csv_import_hint')} href="#/import-csv" />
      </Card>
      <Card>
        <Title title={t('drive_title')} hint={s.driveEmail ? `${s.driveEmail}` : t('drive_web_hint')} />
        {s.driveEmail && <span class="pf-s">{backupSummary(s)}</span>}
        {busy && <span class="pf-s" role="status">{busy}</span>}
        {s.driveEmail && (
          <button
            type="button"
            class="pf-switchrow"
            role="switch"
            aria-checked={autoOn}
            onClick={() => void setAutoBackup(!autoOn)}
          >
            <span class="pf-grow">
              <span class="pf-t">{t('prog41_drive_auto_title')}</span>
              <span class="pf-s">{t('prog41_drive_auto_hint_web')}</span>
            </span>
            <span class={autoOn ? 'pf-switch pf-switch-on' : 'pf-switch'} aria-hidden="true"><span class="pf-knob" /></span>
          </button>
        )}
        <div class="pf-actions">
          {!s.driveEmail ? (
            <Button onClick={() => setNoticeOpen(true)} loading={busy === t('drive_connecting')}>{t('drive_connect')}</Button>
          ) : (
            <>
              {needsReconnect && (
                <Button onClick={() => void backUp()} disabled={!!busy}>{t('prog41_drive_reconnect')}</Button>
              )}
              <Button variant="tonal" onClick={() => void backUp()} disabled={!!busy}>{t('drive_back_up_now')}</Button>
              <Button variant="tonal" onClick={fromDrive} disabled={!!busy}>{t('drive_web_restore')}</Button>
              <Button variant="text" onClick={() => void disconnectDrive()} disabled={!!busy}>{t('drive_disconnect')}</Button>
            </>
          )}
        </div>
      </Card>
      <Card>
        <Title title={t('backup_web_file_title')} hint={t('backup_web_file_hint')} />
        <div class="pf-actions">
          <Button variant="tonal" onClick={saveFile} disabled={!!busy}>{t('backup_web_save_file')}</Button>
          <Button variant="tonal" onClick={() => fileInput.current?.click()} disabled={!!busy}>{t('backup_web_restore_file')}</Button>
          {copies.length > 0 && <Button variant="text" onClick={undo} disabled={!!busy}>{t('profile_undo_restore')}</Button>}
        </div>
        <div class="pf-offline-line">
          <span class="pf-s">
            {`${online ? t('prog41_web_offline_ready') : t('prog41_web_offline_now')} · ${persisted ? t('prog41_web_storage_persisted') : t('prog41_web_storage_not_persisted')}`}
          </span>
          {persisted === false && (
            <Button variant="text" onClick={() => void protectStorage()}>{t('prog41_web_storage_protect')}</Button>
          )}
        </div>
        <input ref={fileInput} type="file" accept=".json,application/json" hidden
          onChange={(e) => { const el = e.target as HTMLInputElement; fromFile(el.files?.[0]); el.value = '' }} />
      </Card>
      <Dialog
        open={noticeOpen}
        title={t('prog41_drive_notice_title')}
        message={t('prog41_drive_notice_body')}
        confirmLabel={t('prog41_drive_notice_confirm')}
        onConfirm={() => { setNoticeOpen(false); void connect() }}
        onCancel={() => setNoticeOpen(false)}
      />
      <Dialog
        open={pending != null}
        title={pending ? (pending.isAutoOffer ? t('prog41_drive_restore_offer_title') : t('restore_title', pending.source)) : ''}
        message={pending ? `${contents(pending.file)}\n\n${pending.isAutoOffer ? t('prog41_drive_restore_offer_body') : t('restore_body')}` : undefined}
        confirmLabel={t('drive_web_restore_confirm')}
        destructive={!pending?.isAutoOffer}
        onConfirm={restore}
        onCancel={() => setPending(null)}
      />
      <Dialog
        open={conflictKind != null}
        title={conflictKind === 'exists' ? t('v42_drive_remote_exists_title') : t('prog41_drive_remote_newer_title')}
        message={conflictKind === 'exists' ? t('v42_drive_remote_exists_body') : t('prog41_drive_remote_newer_body')}
        confirmLabel={conflictKind === 'exists' ? t('v42_drive_restore') : t('drive_web_restore_confirm')}
        secondaryLabel={t('v42_drive_keep_mine')}
        onConfirm={() => { setConflictKind(null); void fromDrive() }}
        onSecondary={() => { setConflictKind(null); setConfirmKeepMine(true) }}
        onCancel={() => setConflictKind(null)}
      />
      <Dialog
        open={confirmKeepMine}
        title={t('v42_drive_keep_mine')}
        message={t('v42_drive_keep_mine_confirm')}
        confirmLabel={t('v42_drive_replace')}
        destructive
        onConfirm={() => { setConfirmKeepMine(false); void backUp(true) }}
        onCancel={() => setConfirmKeepMine(false)}
      />
    </>
  )
}
