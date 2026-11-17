import { useState } from 'preact/hooks'
import { t } from '../../i18n'
import { EXERCISE_MEDIA_KEYS } from '../../data/seed/exercise-media'
import type { ExerciseEntity } from '../../domain/entities'
import { Card } from './index'

export function exerciseMediaKey(ex: Pick<ExerciseEntity, 'id' | 'mediaKey'>): string | null {
  const key = (ex.mediaKey && ex.mediaKey.trim()) || EXERCISE_MEDIA_KEYS[ex.id] || ''
  return key || null
}

export function exerciseMediaUrl(key: string, frame: 0 | 1): string {
  const rawBase = import.meta.env.BASE_URL || '/ForgeQuest/'
  const base = rawBase.endsWith('/') ? rawBase : `${rawBase}/`
  return `${base}ex-media/${key}_${frame}.webp`
}

const DUMBBELL_ICON = (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false">
    <path d="M20.57 14.86L22 13.43 20.57 12 17 15.57 8.43 7 12 3.43 10.57 2 9.14 3.43 7.71 2 5.57 4.14 4.14 2.71 2.71 4.14l1.43 1.43L2 7.71l1.43 1.43L2 10.57 3.43 12 7 8.43 15.57 17 12 20.57 13.43 22l1.43-1.43L16.29 22l2.14-2.14 1.43 1.43 1.43-1.43-1.43-1.43L22 16.29z" />
  </svg>
)

export function ExerciseThumbnail({ exercise, size = 48 }: { exercise: Pick<ExerciseEntity, 'id' | 'mediaKey'>; size?: number }) {
  const key = exerciseMediaKey(exercise)
  const [failed, setFailed] = useState(false)
  return (
    <span
      class="kit-ex-thumb"
      style={{ width: `${size}px`, height: `${size}px`, minWidth: `${size}px`, minHeight: `${size}px` }}
      aria-hidden="true"
    >
      {key && !failed ? (
        <img
          src={exerciseMediaUrl(key, 0)}
          alt=""
          loading="lazy"
          decoding="async"
          onError={() => setFailed(true)}
        />
      ) : DUMBBELL_ICON}
    </span>
  )
}

export function ExerciseMediaPair({ exercise }: { exercise: Pick<ExerciseEntity, 'id' | 'name' | 'mediaKey'> }) {
  const key = exerciseMediaKey(exercise)
  const [failed0, setFailed0] = useState(false)
  const [failed1, setFailed1] = useState(false)

  if (!key) {
    return (
      <Card>
        <div class="kit-ex-nomedia">
          <span class="kit-ex-thumb" style={{ width: '48px', height: '48px' }} aria-hidden="true">
            {DUMBBELL_ICON}
          </span>
          <span class="kit-ex-nomedia-text">{t('detail_no_media')}</span>
        </div>
      </Card>
    )
  }

  return (
    <Card>
      <div class="kit-ex-pair">
        <div class="kit-ex-frame">
          <div class="kit-ex-frame-box">
            {!failed0
              ? <img src={exerciseMediaUrl(key, 0)} alt={`${exercise.name} - ${t('detail_start_pose')}`} loading="lazy" decoding="async" onError={() => setFailed0(true)} />
              : DUMBBELL_ICON}
          </div>
          <span class="kit-ex-frame-cap">{t('detail_start_pose')}</span>
        </div>
        <div class="kit-ex-frame">
          <div class="kit-ex-frame-box">
            {!failed1
              ? <img src={exerciseMediaUrl(key, 1)} alt={`${exercise.name} - ${t('detail_finish_pose')}`} loading="lazy" decoding="async" onError={() => setFailed1(true)} />
              : DUMBBELL_ICON}
          </div>
          <span class="kit-ex-frame-cap">{t('detail_finish_pose')}</span>
        </div>
      </div>
    </Card>
  )
}
