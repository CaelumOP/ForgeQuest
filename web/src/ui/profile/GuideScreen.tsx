import { t, useLang } from '../../i18n'
import { RestaurantIcon } from '../icons'
import { Button, Card, resetCoachMarks, toast } from '../kit'
import { DumbbellIcon } from '../home/glyphs'
import './profile.css'

export type GuideItem = {
  id: 'workout' | 'food' | 'body' | 'progress' | 'friends' | 'backup'
  titleKey: string
  bodyKey: string
  href: string
}

export const GUIDE_ITEMS: readonly GuideItem[] = [
  { id: 'workout', titleKey: 'v42_guide_workout_t', bodyKey: 'v42_guide_workout_b', href: '#/workouts' },
  { id: 'food', titleKey: 'v42_guide_food_t', bodyKey: 'v42_guide_food_b', href: '#/food' },
  { id: 'body', titleKey: 'v42_guide_body_t', bodyKey: 'v42_guide_body_b', href: '#/progress' },
  { id: 'progress', titleKey: 'v42_guide_progress_t', bodyKey: 'v42_guide_progress_b', href: '#/progress' },
  { id: 'friends', titleKey: 'v42_guide_friends_t', bodyKey: 'v42_guide_friends_b', href: '#/friends' },
  { id: 'backup', titleKey: 'v42_guide_backup_t', bodyKey: 'v42_guide_backup_b', href: '#/profile/backup' },
]

const PATHS: Record<Exclude<GuideItem['id'], 'workout' | 'food'>, string> = {
  body: 'M12 3a4 4 0 00-4 4h8a4 4 0 00-4-4zM5 9l-2 12h18L19 9z',
  progress: 'M3.5 18.49l6-6.01 4 4L22 6.92l-1.41-1.41-7.09 7.97-4-4L2 16.99z',
  friends: 'M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z',
  backup: 'M19.35 10.04A7.49 7.49 0 0012 4C9.11 4 6.6 5.64 5.35 8.04A5.994 5.994 0 000 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z',
}

function GuideGlyph({ id }: { id: GuideItem['id'] }) {
  if (id === 'workout') return <DumbbellIcon size={22} />
  if (id === 'food') return <RestaurantIcon size={22} />
  return (
    <svg width={22} height={22} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false">
      <path d={PATHS[id]} />
    </svg>
  )
}

export function GuideScreen(_: { param?: string }) {
  useLang()
  return (
    <div class="content pf">
      {GUIDE_ITEMS.map((item) => (
        <Card key={item.id}>
          <div class="pf-guide-row">
            <span class="pf-ic pf-teal"><GuideGlyph id={item.id} /></span>
            <div class="pf-grow">
              <h2 class="pf-h3">{t(item.titleKey)}</h2>
              <p class="pf-s pf-guide-body">{t(item.bodyKey)}</p>
            </div>
          </div>
          <div class="pf-guide-foot">
            <a class="kit-btn kit-btn-text pf-guide-show" href={item.href}>{t('v42_guide_show_me')}</a>
          </div>
        </Card>
      ))}
      <Button
        block
        variant="tonal"
        onClick={() => {
          resetCoachMarks()
          toast(t('v42_guide_tips_reset'))
        }}
      >
        {t('v42_guide_tips_again')}
      </Button>
    </div>
  )
}
