import { useEffect, useState } from 'preact/hooks'
import { t, useLang } from '../../i18n'
import { useFriends } from '../../data/friends'
import { useLive } from '../../data/live'
import { CloseIcon, ForwardChevron } from '../icons'
import { Card } from '../kit'
import { CheckCircleIcon } from './glyphs'
import {
  buildGettingStartedItems, markFriendDone, markGettingStartedDismissed,
  queryGettingStartedDone, shouldShowGettingStarted, syncFriendDone, hasFriendOrOutgoing,
  type GettingStartedCounts,
} from './getting-started'

const ALL_DONE_DISPLAY_MS = 2800

export function GettingStarted() {
  useLang()
  const [hidden, setHidden] = useState(() => !shouldShowGettingStarted())
  const counts = useLive(queryGettingStartedDone, [], null as GettingStartedCounts | null)
  const friends = useFriends()

  useEffect(() => {
    if (hasFriendOrOutgoing(friends.view)) markFriendDone()
  }, [friends.view])

  const friendDone = syncFriendDone(friends.view)
  const items = counts ? buildGettingStartedItems(counts, friendDone) : null
  const doneCount = items ? items.filter((i) => i.done).length : 0
  const allDone = items != null && doneCount === items.length

  useEffect(() => {
    if (hidden || !allDone) return
    markGettingStartedDismissed()
    const timer = window.setTimeout(() => setHidden(true), ALL_DONE_DISPLAY_MS)
    return () => clearTimeout(timer)
  }, [hidden, allDone])

  if (hidden || !items) return null

  const dismiss = () => {
    markGettingStartedDismissed()
    setHidden(true)
  }

  return (
    <Card class="home-gs">
      <div class="home-gs-head">
        <div class="home-gs-head-text">
          <h2 class="home-t">{t('v42_gs_title')}</h2>
          <span class="home-s home-gs-count">{t('v42_gs_progress', doneCount, items.length)}</span>
        </div>
        <button type="button" class="kit-iconbtn home-gs-close" aria-label={t('v42_gs_hide')} onClick={dismiss}>
          <CloseIcon size={20} />
        </button>
      </div>
      <div class="home-gs-bar" aria-hidden="true">
        <div class="home-gs-bar-fill" style={{ inlineSize: `${(doneCount / items.length) * 100}%` }} />
      </div>
      {allDone ? (
        <div class="home-gs-done" role="status">
          <span class="home-gs-check on" aria-hidden="true"><CheckCircleIcon size={22} /></span>
          <span>{t('v42_gs_all_done')}</span>
        </div>
      ) : (
        <ul class="home-gs-list">
          {items.map((item) => (
            <li key={item.id}>
              <a class={`home-gs-item${item.done ? ' done' : ''}`} href={item.href}>
                <span
                  class={`home-gs-check${item.done ? ' on' : ''}`}
                  role="img"
                  aria-label={t(item.done ? 'v42_gs_item_done' : 'v42_gs_item_todo')}
                >
                  {item.done ? <CheckCircleIcon size={22} /> : <span class="home-gs-ring" aria-hidden="true" />}
                </span>
                <span class="home-gs-label">{t(item.key)}</span>
                <span class="home-gs-chev" aria-hidden="true"><ForwardChevron size={18} /></span>
              </a>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}
