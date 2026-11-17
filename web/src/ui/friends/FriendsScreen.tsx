import { ForgeScoreButton, ForgeScoreInfoButton, ForgeScoreSheet } from './ForgeScoreSheet'
import type { ComponentChildren } from 'preact'
import { useEffect, useId, useRef, useState } from 'preact/hooks'
import { getLang, intlLocale, plural, t, useLang, type Lang } from '../../i18n'
import {
  acceptFriend,
  badgeCount,
  blockFriend,
  clearFriendsError,
  declineFriend,
  effectiveNudgeNextAt,
  inviteFriend,
  joinFriends,
  markInboxSeen,
  nudgeCooldownKind,
  nudgeFriend,
  refreshFriends,
  removeFriend,
  renderGoogleSignInButton,
  setFriendMuted,
  shareText,
  shownName,
  useFriends,
} from '../../data/friends'
import { enablePush, pushPermission, pushSupported } from '../../data/push'
import { useSettings, weightUnit } from '../../data/settings'
import { normalizeUsername, type FriendBadge, type LeaderboardEntry, type Person } from '../../domain/forge-score'
import { todayEpochDay } from '../../domain/dates'
import { formatWeight } from '../../domain/units'
import {
  BellIcon,
  CloseIcon,
  CopyIcon,
  ExpandMoreIcon,
  FlameIcon,
  GroupIcon,
  PersonAddIcon,
  RefreshIcon,
  SettingsIcon,
  ShareOutIcon,
  CheckIcon,
} from '../icons'
import { Button, Card, Dialog, IconButton, reducedMotion, Sheet, TextField, toast } from '../kit'
import { useIsDark } from '../theme'
import { Avatar } from './Avatar'
import { RankBadge } from './RankBadge'
import './friends.css'

const BADGE_KEYS: Record<FriendBadge, string> = {
  trained: 'friends_badge_trained',
  on_track: 'friends_badge_on_track',
  slacking: 'friends_badge_slacking',
  rest: 'friends_badge_rest',
  not_synced: 'friends_badge_not_synced',
}

/** Isolates a Latin username inside a plain translated string (including RTL Arabic sentences). */
const isolateLtr = (s: string): string => `\u2066${s}\u2069`

/** Inbox times from the Worker are epoch SECONDS. */
function relativeTime(epochSec: number, lang: Lang, now = Date.now()): string {
  const diffSec = Math.round(epochSec - now / 1000)
  const rtf = new Intl.RelativeTimeFormat(intlLocale(lang), { numeric: 'auto' })
  const abs = Math.abs(diffSec)
  if (abs < 60) return rtf.format(diffSec, 'second')
  const diffMin = Math.round(diffSec / 60)
  if (Math.abs(diffMin) < 60) return rtf.format(diffMin, 'minute')
  const diffHr = Math.round(diffMin / 60)
  if (Math.abs(diffHr) < 24) return rtf.format(diffHr, 'hour')
  return rtf.format(Math.round(diffHr / 24), 'day')
}

function SwitchRow({ label, on, onToggle, disabled }: { label: string; on: boolean; onToggle: () => void; disabled?: boolean }) {
  return (
    <button type="button" class="fr-switchrow" role="switch" aria-checked={on} disabled={disabled} onClick={onToggle}>
      <span class="fr-switchlabel">{label}</span>
      <span class={on ? 'fr-switch fr-switch-on' : 'fr-switch'} aria-hidden="true"><span class="fr-knob" /></span>
    </button>
  )
}

function SignedOutView({ error }: { error: string | null }) {
  const lang = useLang()
  const isDark = useIsDark()
  const btnRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const el = btnRef.current
    if (!el) return
    const ctl = new AbortController()
    const avail = el.clientWidth
    const width = avail > 0 ? Math.min(280, Math.max(200, Math.floor(avail))) : 260
    void renderGoogleSignInButton(el, {
      theme: isDark ? 'filled_black' : 'outline',
      width,
      locale: lang,
      signal: ctl.signal,
    })
    return () => ctl.abort()
  }, [lang, isDark])
  return (
    <Card class="fr-pitch">
      <span class="fr-pitch-ic" aria-hidden="true"><GroupIcon size={28} /></span>
      <h2 class="fr-h2">{t('friends_pitch_title')}</h2>
      <p class="fr-body">{t('friends_pitch_body')}</p>
      <div ref={btnRef} class="fr-google-wrap" aria-label={t('friends_sign_in_google')} />
      {error && <p class="fr-err" role="alert">{t('friends_error_line', error)}</p>}
    </Card>
  )
}

/** Live format hint for a username field: null while empty or valid, else the bad_username line. */
function usernameError(raw: string): string | undefined {
  return raw.trim() !== '' && normalizeUsername(raw) === null ? t('friends_username_rules') : undefined
}

function JoinView({ busy, error }: { busy: boolean; error: string | null }) {
  const [username, setUsername] = useState('')
  const [sharePhoto, setSharePhoto] = useState(true)
  const [sharePrs, setSharePrs] = useState(true)

  const valid = normalizeUsername(username) !== null
  return (
    <Card class="fr-form">
      <div>
        <h2 class="fr-h2">{t('friends_join_title')}</h2>
        <p class="fr-sub">{t('friends_join_subtitle')}</p>
      </div>
      <TextField
        label={t('friends_username_label')}
        hint={t('friends_username_hint')}
        error={usernameError(username)}
        value={username}
        placeholder="@"
        autocomplete="username"
        onInput={(v) => { clearFriendsError(); setUsername(v.slice(0, 21)) }}
        disabled={busy}
      />
      <div>
        <SwitchRow label={t('friends_share_photo')} on={sharePhoto} onToggle={() => setSharePhoto(!sharePhoto)} disabled={busy} />
        <SwitchRow label={t('friends_share_prs')} on={sharePrs} onToggle={() => setSharePrs(!sharePrs)} disabled={busy} />
      </div>
      <Button
        block
        loading={busy}
        disabled={!valid}
        onClick={() => void joinFriends({ username, sharePhoto, sharePrs })}
      >
        {t('friends_join_button')}
      </Button>
      {error && <p class="fr-err" role="alert">{t('friends_error_line', error)}</p>}
    </Card>
  )
}

async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch { /* fallback below */ }
  return false
}

/** Opens Profile with its Friends group expanded (username, share toggles, alerts, sign out, delete). */
function openFriendsSettings() {
  try {
    const key = 'fq.profile.open'
    const open = JSON.parse(localStorage.getItem(key) ?? '[]')
    const ids = Array.isArray(open) ? open : []
    if (!ids.includes('friends')) localStorage.setItem(key, JSON.stringify([...ids, 'friends']))
  } catch { /* the group just opens closed */ }
  location.hash = '#/profile'
}

/** "Name: done" / "free week" / "not yet" for the shared-streak week line. */
function weekLine(rawName: string, state: LeaderboardEntry['weekState']): string {
  const name = isolateLtr(rawName)
  if (state === 'hit') return t('friends_week_done', name)
  if (state === 'free') return t('friends_week_free', name)
  return t('friends_week_not_yet', name)
}

function statusLine(entry: LeaderboardEntry): string {
  const word = t(BADGE_KEYS[entry.badge])
  return entry.syncedThisWeek ? `${word} · ${plural('friends_done_count', entry.done)}` : word
}

function formatNudgeCooldown(blockedUntil: number, lang: Lang, nowMs: number): string {
  if (nudgeCooldownKind(blockedUntil, nowMs) === 'tomorrow') {
    return t('v42_friends_nudge_tomorrow')
  }
  const time = new Intl.DateTimeFormat(intlLocale(lang), {
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(blockedUntil * 1000))
  return t('v42_friends_nudge_again_at', time)
}

function AnimatedRow({
  leaving,
  class: cls,
  children,
}: {
  leaving: boolean
  class?: string
  children: ComponentChildren
}) {
  const ref = useRef<HTMLDivElement>(null)
  const [gone, setGone] = useState(false)
  const reduced = reducedMotion()

  useEffect(() => {
    if (!leaving) {
      setGone(false)
      return
    }
    if (reduced) {
      setGone(true)
      return
    }
    const timer = setTimeout(() => setGone(true), 300)
    return () => clearTimeout(timer)
  }, [leaving, reduced])

  if (leaving && (gone || reduced)) return null

  const wrapClass = [
    'fq-row-wrap',
    !leaving && 'fq-row-enter',
    leaving && 'fq-leaving',
    cls,
  ].filter(Boolean).join(' ')

  return (
    <div
      ref={ref}
      class={wrapClass}
      onTransitionEnd={(e) => {
        if (leaving && e.target === ref.current && e.propertyName === 'grid-template-rows') {
          setGone(true)
        }
      }}
    >
      <div class="fq-row-inner">{children}</div>
    </div>
  )
}

/** Your @username with Copy/Share, then the add-by-username field. Used in the Add sheet and the empty state. */
function AddFriend({ username, onDone }: { username: string; onDone?: () => void }) {
  const [nameInput, setNameInput] = useState('')
  const [sendState, setSendState] = useState<'idle' | 'sending' | 'sent'>('idle')
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const addInputId = useId()

  useEffect(() => () => {
    if (timerRef.current) clearTimeout(timerRef.current)
  }, [])

  const handleCopy = async () => {
    if (await copyText(`@${username}`)) toast(t('friends_username_copied'))
  }
  const handleShare = async () => {
    const text = shareText(username)
    if (typeof navigator !== 'undefined' && navigator.share) {
      try {
        await navigator.share({ text })
        return
      } catch (err) {
        if ((err as { name?: string })?.name === 'AbortError') return
      }
    }
    if (await copyText(text)) toast(t('friends_share_copied'))
  }
  const handleInvite = async (e?: Event) => {
    e?.preventDefault()
    if (sendState !== 'idle' || normalizeUsername(nameInput) === null) return
    setSendState('sending')
    const res = await inviteFriend(nameInput)
    if (res.ok) {
      setNameInput('')
      setSendState('sent')
      if (res.data.status === 'accepted') toast(t('friends_invite_accepted'))
      if (timerRef.current) clearTimeout(timerRef.current)
      timerRef.current = setTimeout(() => {
        timerRef.current = null
        setSendState('idle')
        onDone?.()
      }, reducedMotion() ? 250 : 750)
    } else {
      setSendState('idle')
    }
  }

  return (
    <div class="fr-addbox">
      <div class="fr-code-head">
        <span class="fr-code-label">{t('friends_your_code')}</span>
        <span class="fr-code-val" dir="ltr">@{username}</span>
      </div>
      <div class="fr-code-actions">
        <Button variant="tonal" icon={<CopyIcon size={18} />} onClick={() => void handleCopy()}>{t('friends_copy_code')}</Button>
        <Button variant="filled" icon={<ShareOutIcon size={18} />} onClick={() => void handleShare()}>{t('friends_share_code')}</Button>
      </div>
      <form class="fr-add-row" onSubmit={(e) => void handleInvite(e)}>
        <div class="fr-add-field">
          <label class="kit-label" for={addInputId}>{t('friends_add_label')}</label>
          <input
            id={addInputId}
            type="text"
            class="kit-input fr-code-input"
            dir="ltr"
            maxLength={21}
            autocapitalize="none"
            autocorrect="off"
            spellcheck={false}
            placeholder={t('friends_add_hint')}
            value={nameInput}
            disabled={sendState === 'sending'}
            onInput={(e) => {
              clearFriendsError()
              setNameInput((e.target as HTMLInputElement).value)
            }}
          />
        </div>
        <Button
          type="submit"
          variant="tonal"
          class={sendState === 'sent' ? 'fr-send-btn fr-send-sent' : 'fr-send-btn'}
          disabled={sendState === 'sending' || (sendState === 'idle' && normalizeUsername(nameInput) === null)}
          aria-busy={sendState === 'sending' ? 'true' : undefined}
        >
          {sendState === 'sending' ? (
            <span key="sending" class="fr-btn-swap">
              <span class="fr-spinner" aria-hidden="true" />
            </span>
          ) : sendState === 'sent' ? (
            <span key="sent" class="fr-btn-swap">
              <CheckIcon size={16} />
              <span>{t('friends_invite_sent')}</span>
            </span>
          ) : (
            <span key="idle" class="fr-btn-swap">
              {t('friends_add_button')}
            </span>
          )}
        </Button>
      </form>
    </div>
  )
}

export function FriendsScreen({ param }: { param?: string }) {
  useLang()
  const state = useFriends()
  const settings = useSettings()
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [scoreEntryId, setScoreEntryId] = useState<string | null>(null)
  const [addOpen, setAddOpen] = useState(false)
  const [inboxOpen, setInboxOpen] = useState(false)
  const [unseenPings, setUnseenPings] = useState<Set<string>>(() => new Set())
  const [outOpen, setOutOpen] = useState(false)
  const [confirmAction, setConfirmAction] = useState<{ kind: 'remove' | 'block'; person: Person } | null>(null)
  const [canPush, setCanPush] = useState(false)
  const [perm, setPerm] = useState(() => pushPermission())
  const [nowMs, setNowMs] = useState(() => Date.now())
  const [nudgeSentIds, setNudgeSentIds] = useState<Set<string>>(() => new Set())
  const nudgeTimersRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map())
  const handledInboxRef = useRef(false)

  useEffect(() => () => {
    for (const timer of nudgeTimersRef.current.values()) clearTimeout(timer)
    nudgeTimersRef.current.clear()
  }, [])

  useEffect(() => {
    void pushSupported().then((ok) => {
      setCanPush(ok)
      setPerm(pushPermission())
    })
  }, [state.status])

  useEffect(() => {
    if (state.status === 'joined' && !state.demoMode) {
      void refreshFriends(true)
    }
  }, [state.status === 'joined'])

  const openInbox = () => {
    const pings = state.view?.inbox.pings ?? []
    setUnseenPings(new Set(pings.filter((p) => !p.seen).map((p) => `${p.fromId}:${p.day}`)))
    setInboxOpen(true)
    void markInboxSeen()
  }

  useEffect(() => {
    if (param !== 'inbox') {
      handledInboxRef.current = false
      return
    }
    if (!handledInboxRef.current && !state.loading && state.status === 'joined' && state.view) {
      handledInboxRef.current = true
      openInbox()
      try {
        history.replaceState(history.state, '', '#/friends')
      } catch {
        // ignore in test environments
      }
    }
  }, [param, state.loading, state.status, state.view])

  const view = state.view
  const selectedEntry: LeaderboardEntry | null =
    selectedId != null ? state.leaderboard.find((e) => !e.isMe && e.person.id === selectedId) ?? null : null
  const selectedFriend = selectedEntry && view ? view.friends.find((f) => f.id === selectedEntry.person.id) ?? null : null
  const scoreEntry: LeaderboardEntry | null =
    scoreEntryId != null ? state.leaderboard.find((e) => e.person.id === scoreEntryId) ?? null : null

  const nowSec = Math.floor(nowMs / 1000)
  let minUntilSec: number | null = null
  if (view) {
    for (const f of view.friends) {
      const until = effectiveNudgeNextAt(f.nudgeNextAt, state.nudgeNextAt[f.id], nowSec)
      if (until !== null && (minUntilSec === null || until < minUntilSec)) {
        minUntilSec = until
      }
    }
  }

  useEffect(() => {
    if (minUntilSec == null) return
    const delay = Math.min(2_147_483_647, Math.max(50, minUntilSec * 1000 - Date.now() + 50))
    const timer = setTimeout(() => setNowMs(Date.now()), delay)
    return () => clearTimeout(timer)
  }, [minUntilSec])

  useEffect(() => {
    const onVis = () => {
      if (document.visibilityState === 'visible') setNowMs(Date.now())
    }
    document.addEventListener('visibilitychange', onVis)
    return () => document.removeEventListener('visibilitychange', onVis)
  }, [])

  const lang = getLang()
  const ar = lang === 'ar'
  const unit = weightUnit(settings)

  const nudgeStateFor = (id: string) => {
    const justSent = nudgeSentIds.has(id)
    const friend = view?.friends.find((f) => f.id === id)
    const blockedUntil = effectiveNudgeNextAt(friend?.nudgeNextAt, state.nudgeNextAt[id], nowSec)
    return { justSent, blockedUntil, pending: state.pendingIds.has(id) }
  }

  const nudge = async (p: Person) => {
    const { justSent, blockedUntil, pending } = nudgeStateFor(p.id)
    if (justSent || blockedUntil !== null || pending) return
    setNudgeSentIds((prev) => {
      const next = new Set(prev)
      next.add(p.id)
      return next
    })
    try { navigator.vibrate?.(15) } catch { /* unsupported */ }
    const startedAt = Date.now()
    const res = await nudgeFriend(p.id)
    if (res.ok) {
      const wait = Math.max(0, 900 - (Date.now() - startedAt))
      const old = nudgeTimersRef.current.get(p.id)
      if (old) clearTimeout(old)
      const timer = setTimeout(() => {
        nudgeTimersRef.current.delete(p.id)
        setNudgeSentIds((prev) => {
          if (!prev.has(p.id)) return prev
          const next = new Set(prev)
          next.delete(p.id)
          return next
        })
      }, wait)
      nudgeTimersRef.current.set(p.id, timer)
    } else {
      setNudgeSentIds((prev) => {
        if (!prev.has(p.id)) return prev
        const next = new Set(prev)
        next.delete(p.id)
        return next
      })
      if (res.error !== 'already_nudged') {
        toast(t('v42_friends_action_failed'))
      }
    }
  }

  const handleAccept = async (id: string) => {
    const res = await acceptFriend(id)
    if (res.ok) toast(t('v42_friends_now_friends'))
    else toast(t('v42_friends_action_failed'))
  }

  const handleDecline = async (id: string) => {
    const res = await declineFriend(id)
    if (!res.ok) toast(t('v42_friends_action_failed'))
  }

  const today = view ? Math.max(view.today, todayEpochDay()) : 0
  const daysLeft = view ? Math.max(0, view.weekStart + 6 - today) : 0
  const myRank = state.leaderboard.findIndex((e) => e.isMe) + 1
  const myEntry = myRank > 0 ? state.leaderboard[myRank - 1] : null
  const mine = view?.mySnapshot
  const myWeekState = mine && view && mine.weekStart >= view.weekStart ? mine.weekState ?? null : null

  const count = badgeCount(view)
  const inboxPings = [...(view?.inbox.pings ?? [])].sort((a, b) => b.createdAt - a.createdAt)
  const inboxActivity = [...(view?.inbox.activity ?? [])].sort((a, b) => b.at - a.at)
  const inboxIncoming = view?.incoming ?? []
  const inboxEmpty = inboxIncoming.length === 0 && inboxPings.length === 0 && inboxActivity.length === 0

  return (
    <div class="content fr">
      {state.loading && <p class="fr-sub">…</p>}
      {!state.loading && state.status === 'signed_out' && <SignedOutView error={state.error} />}
      {!state.loading && state.status === 'needs_join' && <JoinView busy={state.busy} error={state.error} />}

      {!state.loading && state.status === 'joined' && view && (
        <>
          <div class="fr-actions">
            <ForgeScoreInfoButton onClick={() => setScoreEntryId(myEntry?.person.id ?? view.me.id)} />
            <IconButton label={t('friends_add_title')} tone="tonal" onClick={() => setAddOpen(true)}><PersonAddIcon /></IconButton>
            <button
              type="button"
              class="kit-iconbtn fr-bell-btn"
              aria-label={count > 0 ? t('friends_inbox_badge_aria', count) : t('friends_inbox_title')}
              onClick={openInbox}
            >
              <BellIcon />
              {count > 0 && <span class="fr-bell-badge" aria-hidden="true">{count}</span>}
            </button>
            <IconButton label={t('friends_refresh')} disabled={state.refreshing} onClick={() => void refreshFriends(false)}><RefreshIcon /></IconButton>
            <IconButton label={t('friends_settings')} onClick={openFriendsSettings}><SettingsIcon /></IconButton>
          </div>

          {state.error && <p class="fr-err" role="alert">{t('friends_error_line', state.error)}</p>}

          {view.friends.length === 0 ? (
            <Card class="fr-empty">
              <span class="fr-pitch-ic" aria-hidden="true"><GroupIcon size={28} /></span>
              <h2 class="fr-h2">{t('friends_empty_title')}</h2>
              <p class="fr-body">{t('friends_empty_board')}</p>
              <AddFriend username={view.me.username ?? ''} />
            </Card>
          ) : (
            <>
              <div class="fr-week">
                <span><b>{t('friends_board_title')}</b> · {daysLeft === 0 ? t('friends_last_day') : plural('friends_days_left', daysLeft)}</span>
                {myEntry?.syncedThisWeek && <RankBadge rank={myRank} total={state.leaderboard.length} lang={lang} />}
              </div>
              <Card class="fr-board" padded={false}>
                {state.leaderboard.map((entry, idx) => {
                  const { justSent, blockedUntil, pending } = nudgeStateFor(entry.person.id)
                  const canNudge = !entry.isMe && entry.weekState !== 'hit'
                  const nudgeDisabled = justSent || blockedUntil !== null || pending
                  const nudgeKey = justSent ? 'sent' : blockedUntil !== null ? 'cd' : 'go'
                  const nudgeLabel = justSent
                    ? t('friends_nudge_sent')
                    : blockedUntil !== null
                      ? formatNudgeCooldown(blockedUntil, lang, nowMs)
                      : t('friends_go_train')
                  const displayName = entry.isMe ? t('friends_you') : shownName(entry.person)
                  const body = (
                    <>
                      <RankBadge
                        rank={entry.syncedThisWeek ? idx + 1 : null}
                        total={state.leaderboard.length}
                        lang={lang}
                        hideUnsyncedA11y
                      />
                      <Avatar person={entry.person} />
                      <span class="fr-board-mid">
                        <span class="fr-name">{displayName}</span>
                        {entry.sharedStreak > 0 && (
                          <span class="fr-flame">
                            <FlameIcon size={14} />
                            <span>{plural('friends_together', entry.sharedStreak)}</span>
                          </span>
                        )}
                        <span class="fr-status">{statusLine(entry)}</span>
                      </span>

                    </>
                  )
                  return (
                    <AnimatedRow
                      key={entry.person.id}
                      leaving={!entry.isMe && state.hiddenIds.has(entry.person.id)}
                      class={entry.isMe ? 'fr-board-row-wrap fr-board-me' : 'fr-board-row-wrap'}
                    >
                      <div class="fr-board-row">
                        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--sp-xs)' }}>
                          {entry.isMe
                            ? <div class="fr-row-main" style={{ flex: 1 }}>{body}</div>
                            : <button type="button" class="fr-row-main" style={{ flex: 1 }} onClick={() => setSelectedId(entry.person.id)}>{body}</button>}
                          <ForgeScoreButton score={entry.syncedThisWeek ? entry.score : null}
                            done={entry.done} target={entry.target} onClick={() => setScoreEntryId(entry.person.id)} />
                        </div>
                        {canNudge && (
                          <div class="fr-nudge-row">
                            <button
                              type="button"
                              class={justSent ? 'fr-pill fr-pill-sent' : blockedUntil !== null ? 'fr-pill fr-pill-done' : 'fr-pill'}
                              disabled={nudgeDisabled}
                              onClick={() => void nudge(entry.person)}
                            >
                              <span key={nudgeKey} class="fr-btn-swap">
                                {justSent && <CheckIcon size={16} />}
                                <span>{nudgeLabel}</span>
                              </span>
                            </button>
                          </div>
                        )}
                      </div>
                    </AnimatedRow>
                  )
                })}
              </Card>
            </>
          )}

          {view.outgoing.length > 0 && (
            <div class="fr-out">
              <button type="button" class="fr-out-toggle" aria-expanded={outOpen} onClick={() => setOutOpen(!outOpen)}>
                <span class="fr-grow">{plural('friends_invites_waiting', view.outgoing.length)}</span>
                <span class={outOpen ? 'fr-chev fr-chev-open' : 'fr-chev'} aria-hidden="true"><ExpandMoreIcon size={20} /></span>
              </button>
              {outOpen && view.outgoing.map((p) => {
                const name = shownName(p)
                return (
                  <AnimatedRow key={p.id} leaving={state.hiddenIds.has(p.id)}>
                    <div class="fr-out-row">
                      <Avatar person={p} />
                      <span class="fr-name fr-grow">{name}</span>
                      <IconButton
                        label={t('friends_cancel_invite', isolateLtr(name))}
                        disabled={state.pendingIds.has(p.id)}
                        onClick={() => void handleDecline(p.id)}
                      >
                        <CloseIcon size={20} />
                      </IconButton>
                    </div>
                  </AnimatedRow>
                )
              })}
            </div>
          )}
        </>
      )}

      <ForgeScoreSheet open={scoreEntryId !== null} onClose={() => setScoreEntryId(null)}
        done={scoreEntry?.done ?? 0} target={scoreEntry?.target ?? 0}
        prCount={scoreEntry?.prCount ?? 0} proteinDays={scoreEntry?.proteinDays ?? 0}
        score={scoreEntry?.syncedThisWeek ? scoreEntry.score : null}
        breakdown={scoreEntry?.breakdown ?? null} />

      <Sheet open={addOpen} onClose={() => setAddOpen(false)} title={t('friends_add_title')}>
        {view && <AddFriend username={view.me.username ?? ''} onDone={() => setAddOpen(false)} />}
        {state.error && <p class="fr-err" role="alert">{t('friends_error_line', state.error)}</p>}
      </Sheet>

      <Sheet open={inboxOpen} onClose={() => setInboxOpen(false)} title={t('friends_inbox_title')}>
        <div class="fr-inbox">
          {canPush && perm === 'default' && (
            <div class="fr-notifs-row">
              <span class="fr-grow fr-body">{t('friends_turn_on_notifs')}</span>
              <Button
                variant="tonal"
                onClick={async () => {
                  await enablePush()
                  setPerm(pushPermission())
                }}
              >
                {t('friends_turn_on_notifs_btn')}
              </Button>
            </div>
          )}

          {inboxEmpty ? (
            <p class="fr-sub">{t('friends_inbox_empty')}</p>
          ) : (
            <>
              {inboxIncoming.length > 0 && (
                <section class="fr-inbox-sec">
                  <h3 class="fr-h3">{t('friends_incoming_title')}</h3>
                  {inboxIncoming.map((p) => {
                    const rowPending = state.pendingIds.has(p.id)
                    return (
                      <AnimatedRow key={p.id} leaving={state.hiddenIds.has(p.id)}>
                        <div class="fr-banner fr-req">
                          <Avatar person={p} />
                          <span class="fr-grow fr-req-text">
                            <span class="fr-name">{shownName(p)}</span>
                            <span class="fr-sub">{t('friends_wants_friends')}</span>
                          </span>
                          <span class="fr-req-actions">
                            <Button variant="text" disabled={rowPending} onClick={() => void handleDecline(p.id)}>{t('friends_decline')}</Button>
                            <Button variant="filled" disabled={rowPending} onClick={() => void handleAccept(p.id)}>{t('friends_accept')}</Button>
                          </span>
                        </div>
                      </AnimatedRow>
                    )
                  })}
                </section>
              )}

              {inboxPings.length > 0 && (
                <section class="fr-inbox-sec">
                  <h3 class="fr-h3">{t('friends_inbox_pings_title')}</h3>
                  {inboxPings.map((p) => {
                    const key = `${p.fromId}:${p.day}`
                    const unseen = unseenPings.has(key)
                    return (
                      <div key={key} class="fr-inbox-item">
                        {unseen && <span class="fr-dot" aria-hidden="true" />}
                        <span class="fr-grow">
                          <span class="fr-inbox-msg">{t('friends_inbox_ping_msg', isolateLtr(shownName(p)))}</span>
                          <span class="fr-sub">{relativeTime(p.createdAt, lang)}</span>
                        </span>
                      </div>
                    )
                  })}
                </section>
              )}

              {inboxActivity.length > 0 && (
                <section class="fr-inbox-sec">
                  <h3 class="fr-h3">{t('friends_inbox_activity_title')}</h3>
                  {inboxActivity.map((a) => (
                    <div key={`${a.userId}:${a.at}`} class="fr-inbox-item">
                      <span class="fr-grow">
                        <span class="fr-inbox-msg">{t('friends_inbox_activity_msg', isolateLtr(shownName(a)))}</span>
                        <span class="fr-sub">{relativeTime(a.at, lang)}</span>
                      </span>
                    </div>
                  ))}
                </section>
              )}
            </>
          )}
        </div>
      </Sheet>

      <Sheet
        open={selectedEntry !== null}
        onClose={() => setSelectedId(null)}
        title={selectedEntry ? shownName(selectedEntry.person) : ''}
      >
        {selectedEntry && (() => {
          const name = shownName(selectedEntry.person)
          const iso = isolateLtr(name)
          const muted = selectedFriend?.muted ?? false
          const { justSent, blockedUntil, pending } = nudgeStateFor(selectedEntry.person.id)
          const nudgeDisabled = justSent || blockedUntil !== null || pending
          const nudgeKey = justSent ? 'sent' : blockedUntil !== null ? 'cd' : 'go'
          const nudgeLabel = justSent
            ? t('friends_nudge_sent')
            : blockedUntil !== null
              ? formatNudgeCooldown(blockedUntil, lang, nowMs)
              : t('friends_go_train')
          return (
            <div class="fr-sheet">
              <div class="fr-together">
                <div class="fr-together-big">
                  <FlameIcon size={22} />
                  <span>{selectedEntry.sharedStreak > 0 ? plural('friends_together', selectedEntry.sharedStreak) : t('friends_together_start')}</span>
                </div>
                {selectedEntry.sharedStreak > 0 && <p class="fr-sub">{t('friends_together_sub')}</p>}
                <div class="fr-together-week">
                  <span class={myWeekState === 'hit' ? 'fr-wk-ok' : undefined}>{myWeekState === 'hit' && <CheckIcon size={16} />}{weekLine(t('friends_you'), myWeekState)}</span>
                  <span class={selectedEntry.weekState === 'hit' ? 'fr-wk-ok' : undefined}>{selectedEntry.weekState === 'hit' && <CheckIcon size={16} />}{weekLine(name, selectedEntry.weekState)}</span>
                </div>
              </div>

              <Button
                variant="filled"
                block
                disabled={nudgeDisabled}
                onClick={() => void nudge(selectedEntry.person)}
              >
                <span key={nudgeKey} class="fr-btn-swap">
                  {justSent && <CheckIcon size={16} />}
                  <span>{nudgeLabel}</span>
                </span>
              </Button>

              <SwitchRow
                label={t('friends_alert_when_trains', iso)}
                on={!muted}
                disabled={pending}
                onToggle={() => void setFriendMuted(selectedEntry.person.id, !muted)}
              />

              <dl class="fr-kv">
                <div><dt>{t('friends_score_label')}</dt><dd>{selectedEntry.syncedThisWeek ? selectedEntry.score : '–'}</dd></div>
                <div><dt>{t(BADGE_KEYS[selectedEntry.badge])}</dt><dd>{plural('friends_done_count', selectedEntry.done)}</dd></div>
                {selectedEntry.syncedThisWeek && (
                  <>
                    <div><dt>{t('friends_their_streak', iso)}</dt><dd>{plural('friends_weeks', selectedEntry.streakWeeks)}</dd></div>
                    <div><dt>{t('friends_protein_label')}</dt><dd>{plural('friends_days', selectedEntry.proteinDays)}</dd></div>
                  </>
                )}
              </dl>

              <h3 class="fr-h3">{t('friends_prs_title')}</h3>
              {selectedEntry.prs.length === 0 ? (
                <p class="fr-sub">{t('friends_prs_hidden')}</p>
              ) : (
                <div class="fr-pr-list">
                  {selectedEntry.prs.map((pr, i) => (
                    <div key={`${pr.exercise}-${i}`} class="fr-pr-row">
                      <span class="fr-pr-name">{pr.exercise}</span>
                      <span class="fr-pr-val">
                        {pr.kg > 0
                          ? `${formatWeight(unit, pr.kg, ar)} × ${plural('friends_pr_reps', pr.reps)}`
                          : plural('friends_pr_reps', pr.reps)}
                      </span>
                    </div>
                  ))}
                </div>
              )}

              <div class="fr-sheet-danger">
                <Button
                  variant="text"
                  class="fr-neutral"
                  disabled={pending}
                  onClick={() => setConfirmAction({ kind: 'remove', person: selectedEntry.person })}
                >
                  {t('friends_remove')}
                </Button>
                <Button
                  variant="text"
                  class="kit-danger"
                  disabled={pending}
                  onClick={() => setConfirmAction({ kind: 'block', person: selectedEntry.person })}
                >
                  {t('friends_block')}
                </Button>
              </div>
            </div>
          )
        })()}
      </Sheet>

      <Dialog
        open={confirmAction !== null}
        title={t(confirmAction?.kind === 'block' ? 'friends_block_confirm_title' : 'friends_remove_confirm_title')}
        message={
          confirmAction
            ? t(
                confirmAction.kind === 'block' ? 'friends_block_confirm_body' : 'friends_remove_confirm_body',
                isolateLtr(shownName(confirmAction.person)),
              )
            : undefined
        }
        confirmLabel={t(confirmAction?.kind === 'block' ? 'friends_block' : 'friends_remove')}
        cancelLabel={t('friends_cancel')}
        destructive
        onCancel={() => setConfirmAction(null)}
        onConfirm={() => {
          const target = confirmAction
          setConfirmAction(null)
          if (!target) return
          setSelectedId(null)
          void (async () => {
            const res = target.kind === 'block'
              ? await blockFriend(target.person.id)
              : await removeFriend(target.person.id)
            if (!res.ok) toast(t('v42_friends_action_failed'))
          })()
        }}
      />
    </div>
  )
}
