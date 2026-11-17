import { useEffect, useState } from 'preact/hooks'
import { intlLocale, t } from '../../i18n'
import type { ExerciseRow, SessionRow, SetRow } from '../../data/db'
import { exerciseCueKeys } from '../../data/seed/exercise-cues'
import { weightNumber, type WeightUnit } from '../../domain/units'
import { Button, Segmented, Sheet } from '../kit'
import { openFormVideo, kindLabel, musclesLabel } from '../workouts/helpers'

export type PeekHistoryEntry = {
  session: SessionRow
  sets: SetRow[]
}

export type QuickPeekSheetProps = {
  open: boolean
  exercise: ExerciseRow | null
  unit: WeightUnit
  history: PeekHistoryEntry[]
  canUseLastTime: boolean
  onClose: () => void
  onEditSetupNote: () => void
  onUseLastTime: () => void
  onFullStats: (exerciseId: string) => void
}

function formatShortDate(epochDay: number): string {
  return new Date(epochDay * 86_400_000).toLocaleDateString(intlLocale(), {
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC',
  })
}

export function QuickPeekSheet({
  open,
  exercise,
  unit,
  history,
  canUseLastTime,
  onClose,
  onEditSetupNote,
  onUseLastTime,
  onFullStats,
}: QuickPeekSheetProps) {
  const [tab, setTab] = useState<'form' | 'history'>('form')
  const [err0, setErr0] = useState(false)
  const [err1, setErr1] = useState(false)

  const mediaKey = exercise ? (exercise.mediaKey?.trim() || exercise.id) : ''
  useEffect(() => {
    setErr0(false)
    setErr1(false)
  }, [mediaKey, open])

  if (!open || !exercise) return null

  const base = import.meta.env.BASE_URL ?? './'
  const src0 = `${base}ex-media/${encodeURIComponent(mediaKey)}_0.webp`
  const src1 = `${base}ex-media/${encodeURIComponent(mediaKey)}_1.webp`
  const bothMissing = err0 && err1
  const cues = exerciseCueKeys(exercise.id)
  const subtitle = [kindLabel(exercise.kind), musclesLabel(exercise) || null].filter(Boolean).join(' · ')
  const sec = t('unit_s')

  return (
    <Sheet open={open} onClose={onClose} title={exercise.name}>
      <div class="lw-peek">
        {subtitle && <div class="lw-small">{subtitle}</div>}
        <Segmented
          label={exercise.name}
          options={[
            { value: 'form', label: t('live41_peek_tab_form') },
            { value: 'history', label: t('live41_peek_tab_history') },
          ]}
          value={tab}
          onChange={(v: 'form' | 'history') => setTab(v)}
        />

        {tab === 'form' ? (
          <div class="lw-peek-section">
            {bothMissing ? (
              <div class="lw-peek-placeholder">
                <svg width="28" height="28" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                  <path fill="currentColor" d="M20.57 14.86 22 13.43 20.57 12 17 15.57 8.43 7 12 3.43 10.57 2 9.14 3.43 7.71 2 5.57 4.14 4.14 2.71 2.71 4.14l1.43 1.43L2 7.71l1.43 1.43L2 10.57 3.43 12 7 8.43 15.57 17 12 20.57 13.43 22l1.43-1.43L16.29 22l2.14-2.14 1.43 1.43 1.43-1.43-1.43-1.43L22 16.29z" />
                </svg>
                <span class="lw-small">{t('live41_peek_no_media')}</span>
              </div>
            ) : (
              <div class="lw-peek-media-grid">
                <figure class="lw-peek-tile">
                  <div class="lw-peek-imgbox">
                    {!err0 ? (
                      <img src={src0} alt={t('live41_peek_start')} loading="lazy" onError={() => setErr0(true)} />
                    ) : (
                      <span class="lw-small">—</span>
                    )}
                  </div>
                  <figcaption class="lw-small">{t('live41_peek_start')}</figcaption>
                </figure>
                <figure class="lw-peek-tile">
                  <div class="lw-peek-imgbox">
                    {!err1 ? (
                      <img src={src1} alt={t('live41_peek_finish')} loading="lazy" onError={() => setErr1(true)} />
                    ) : (
                      <span class="lw-small">—</span>
                    )}
                  </div>
                  <figcaption class="lw-small">{t('live41_peek_finish')}</figcaption>
                </figure>
              </div>
            )}

            <div class="lw-peek-box lw-peek-setup">
              <div class="lw-peek-setup-text">
                <span class="lw-small">{t('live41_setup_note_label')}</span>
                <span class={exercise.setupNote ? 'lw-peek-setup-val' : 'lw-small'}>
                  {exercise.setupNote || t('live41_setup_note_hint')}
                </span>
              </div>
              <Button variant="text" onClick={onEditSetupNote}>{t('live41_setup_note_edit')}</Button>
            </div>

            <div class="lw-peek-box">
              <div class="lw-peek-box-title">{t('live41_peek_cues')}</div>
              {cues ? (
                <>
                  <p class="lw-peek-cue">{`• ${t(cues[0])}`}</p>
                  <p class="lw-peek-cue">{`• ${t(cues[1])}`}</p>
                  <div class="lw-small" style={{ marginBlockStart: '8px', fontWeight: 500 }}>{t('live41_peek_mistake')}</div>
                  <p class="lw-small" style={{ margin: '2px 0 0' }}>{t(cues[2])}</p>
                </>
              ) : (
                <p class="lw-small" style={{ margin: 0 }}>{t('live41_peek_no_cues')}</p>
              )}
            </div>

            <div class="lw-peek-actions">
              <Button variant="tonal" onClick={() => openFormVideo(exercise)}>{t('cd_watch_form')}</Button>
              <Button variant="tonal" onClick={() => onFullStats(exercise.id)}>{t('live41_peek_full_stats')}</Button>
            </div>
          </div>
        ) : (
          <div class="lw-peek-section">
            {history.length === 0 ? (
              <div class="lw-peek-placeholder">
                <span class="lw-small">{t('live41_peek_no_history')}</span>
              </div>
            ) : (
              <>
                {history.slice(0, 3).map(({ session, sets }) => (
                  <div key={session.id} class="lw-peek-hist-row">
                    <span class="lw-peek-hist-date">{formatShortDate(session.epochDay)}</span>
                    <span class="lw-peek-hist-sets">
                      {sets.map((s) =>
                        exercise.kind === 'HOLD'
                          ? `${s.seconds ?? 0}${sec}`
                          : exercise.kind === 'WEIGHTED'
                            ? `${s.reps ?? 0}×${weightNumber(unit, s.weightKg ?? 0)}`
                            : `${s.reps ?? 0}`
                      ).join('  ')}
                    </span>
                    {sets.some((s) => s.isPr) && <span class="lw-pr">{t('live_pr')}</span>}
                  </div>
                ))}
                {canUseLastTime && (
                  <Button variant="tonal" block onClick={onUseLastTime}>{t('live41_peek_use_last')}</Button>
                )}
              </>
            )}
            <div class="lw-actions">
              <Button variant="text" onClick={() => onFullStats(exercise.id)}>{t('live41_peek_full_stats')}</Button>
            </div>
          </div>
        )}
      </div>
    </Sheet>
  )
}
