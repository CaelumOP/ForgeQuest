import { useEffect, useState } from 'preact/hooks'
import { t } from '../../i18n'
import { markBadgesSeen, useProgress } from '../../data/progress'
import { BADGES, BADGE_CATEGORIES, type BadgeCategory, type BadgeDef } from '../../domain/badges'
import { Button, Card, Sheet } from '../kit'
import { TabIcon } from '../icons'
import './progress.css'

const tr = (key: string, fallback: string) => { const v = t(key); return v === key ? fallback : v }
const title = (b: BadgeDef) => tr(`badge_${b.id}_title`, b.title)
const desc = (b: BadgeDef) => tr(`badge_${b.id}_desc`, b.description)

const FIRE = 'M19.48 12.35c-1.57-4.08-7.16-4.3-5.81-10.23.1-.44-.37-.78-.75-.55C9.29 3.71 6.68 8 8.87 13.62c.18.46-.36.89-.75.59-1.81-1.37-2-3.34-1.84-4.75.06-.52-.62-.77-.91-.34C4.69 10.16 4 11.84 4 14.37c.38 5.6 5.11 7.32 6.81 7.54 2.43.31 5.06-.14 6.95-1.87 2.08-1.93 2.84-5.01 1.72-7.69zM10.2 17.38c1.44-.35 2.18-1.39 2.38-2.31.33-1.43-.96-2.83-.09-5.09.33 1.87 3.27 3.04 3.27 5.08 0 2.53-2.74 4.7-5.56 2.32z'
const STAR = 'M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z'
function CategoryIcon({ cat }: { cat: BadgeCategory }) {
  if (cat === 'STRENGTH') return <TabIcon name="fitness" filled={false} />
  if (cat === 'NUTRITION') return <TabIcon name="restaurant" filled={false} />
  return <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false"><path d={cat === 'CONSISTENCY' ? FIRE : STAR} /></svg>
}

/** All badges by category. Opening the screen marks new ones as seen once you leave it, like Android. */
export function BadgesScreen(_: { param?: string }) {
  const snap = useProgress()
  const [open, setOpen] = useState<BadgeDef | null>(null)
  useEffect(() => () => { void markBadgesSeen() }, [])
  if (!snap) return <div class="content" />
  const owned = new Map(snap.badges.map((b) => [b.id, b]))
  return (
    <div class="content">
      <div class="pg-head">
        <h2 class="pg-title">{t('profile_badges')}</h2>
        <p class="pg-sub">{t('profile_badges_unlocked', snap.badges.length, BADGES.length)}</p>
      </div>
      <div class="pg-list">
        {BADGE_CATEGORIES.map((cat) => (
          <Card key={cat}>
            <h3 class="pg-h3">{t(`badge_cat_${cat.toLowerCase()}`)}</h3>
            <div class="bd-grid">
              {BADGES.filter((b) => b.category === cat).map((b) => {
                const got = owned.get(b.id)
                const isNew = got != null && !got.seen
                return (
                  <button key={b.id} type="button" class={`bd${got ? ' on' : ''}`} onClick={() => setOpen(b)}>
                    <span class="bd-circle">
                      <CategoryIcon cat={cat} />
                      {isNew && <i class="bd-new" />}
                    </span>
                    <span class="bd-title">{title(b)}</span>
                    <span class="kit-sr">{got ? t('progress_web_badge_unlocked') : t('progress_web_badge_locked')}{isNew ? `, ${t('progress_web_badge_new')}` : ''}</span>
                  </button>
                )
              })}
            </div>
          </Card>
        ))}
      </div>
      <Sheet open={open != null} onClose={() => setOpen(null)} title={open ? title(open) : ''}>
        {open && (
          <>
            <p class="pg-hint">{owned.has(open.id) ? t('badge_unlocked_detail', desc(open)) : t('badge_locked_detail', desc(open))}</p>
            <div class="bd-ok"><Button variant="text" onClick={() => setOpen(null)}>{t('action_ok')}</Button></div>
          </>
        )}
      </Sheet>
    </div>
  )
}
