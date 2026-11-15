import { useEffect, useId, useRef, useState } from 'preact/hooks'
import { getLang, plural, t, useLang } from '../../i18n'
import {
  acceptFriend,
  blockFriend,
  clearFriendsError,
  declineFriend,
  dismissFriendsNudge,
  inviteFriend,
  joinFriends,
  nudgeFriend,
  refreshFriends,
  removeFriend,
  nudgeKey,
  renderGoogleSignInButton,
  shareText,
  useFriends,
} from '../../data/friends'
import { useSettings, weightUnit } from '../../data/settings'
import { normalizeUsername, type FriendBadge, type LeaderboardEntry, type Person } from '../../domain/forge-score'
import { formatWeight } from '../../domain/units'
import {
  BellIcon,
  CloseIcon,
  CopyIcon,
  FlameIcon,
  GroupIcon,
  RefreshIcon,
  RestaurantIcon,
  ShareOutIcon,
} from '../icons'
import { Button, Card, Dialog, IconButton, SectionHeader, Sheet, TextField, toast } from '../kit'
import { Avatar } from './Avatar'
import './friends.css'

const BADGE_KEYS: Record<FriendBadge, string> = {
  trained: 'friends_badge_trained',
  on_track: 'friends_badge_on_track',
  slacking: 'friends_badge_slacking',
  rest: 'friends_badge_rest',
  not_synced: 'friends_badge_not_synced',
}


function PersonName({ person, grow }: { person: Person; grow?: boolean }) {
  return (
    <span class={grow ? 'fr-person fr-grow' : 'fr-person'}>
      <span class="fr-name">{person.name}</span>
      {person.username && <span class="fr-handle" dir="ltr">@{person.username}</span>}
    </span>
  )
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
  const btnRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    void renderGoogleSignInButton(btnRef.current)
  }, [])
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

function JoinView({ suggestedName, busy, error }: { suggestedName: string; busy: boolean; error: string | null }) {
  const [name, setName] = useState(suggestedName.slice(0, 40))
  const [username, setUsername] = useState('')
  const [sharePhoto, setSharePhoto] = useState(true)
  const [sharePrs, setSharePrs] = useState(true)
  useEffect(() => {
    setName((cur) => (cur === '' && suggestedName ? suggestedName.slice(0, 40) : cur))
  }, [suggestedName])

  const valid = name.trim().length >= 1 && name.trim().length <= 40 && normalizeUsername(username) !== null
  return (
    <Card class="fr-form">
      <div>
        <h2 class="fr-h2">{t('friends_join_title')}</h2>
        <p class="fr-sub">{t('friends_join_subtitle')}</p>
      </div>
      <TextField
        label={t('friends_name_label')}
        hint={t('friends_name_hint')}
        value={name}
        onInput={(v) => { clearFriendsError(); setName(v.slice(0, 40)) }}
        disabled={busy}
      />
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
        onClick={() => void joinFriends({ name, username, sharePhoto, sharePrs })}
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

export function FriendsScreen() {
  useLang()
  const state = useFriends()
  const settings = useSettings()
  const [nameInput, setNameInput] = useState('')
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [confirmAction, setConfirmAction] = useState<{ kind: 'remove' | 'block'; person: Person } | null>(null)
  const addInputId = useId()

  useEffect(() => {
    if (state.status === 'joined' && !state.demoMode) {
      void refreshFriends(true)
    }
  }, [state.status === 'joined'])

  const view = state.view
  const selectedEntry: LeaderboardEntry | null =
    selectedId != null ? state.leaderboard.find((e) => !e.isMe && e.person.id === selectedId) ?? null : null

  const ar = getLang() === 'ar'
  const unit = weightUnit(settings)

  const handleCopy = async (username: string) => {
    if (await copyText(`@${username}`)) toast(t('friends_username_copied'))
  }

  const handleShare = async (username: string) => {
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
    if (normalizeUsername(nameInput) === null) return
    const res = await inviteFriend(nameInput)
    if (res.ok) {
      setNameInput('')
      toast(t(res.data.status === 'accepted' ? 'friends_invite_accepted' : 'friends_invite_sent'))
    }
  }

  return (
    <div class="content fr">
      {state.loading && <p class="fr-sub">…</p>}
      {!state.loading && state.status === 'signed_out' && <SignedOutView error={state.error} />}
      {!state.loading && state.status === 'needs_join' && (
        <JoinView
          suggestedName={state.auth?.suggestedName ?? ''}
          busy={state.busy}
          error={state.error}
        />
      )}

      {!state.loading && state.status === 'joined' && view && (
        <>
          {view.nudges
            .filter((n) => !state.dismissedNudgeIds.has(nudgeKey(n)))
            .map((n) => (
              <div key={nudgeKey(n)} class="fr-nudge-banner" role="status">
                <span class="fr-nudge-ic" aria-hidden="true"><BellIcon size={20} /></span>
                <span class="fr-nudge-text">{t('friends_nudged_you', n.name)}</span>
                <IconButton label={t('cd_close')} onClick={() => dismissFriendsNudge(nudgeKey(n))}>
                  <CloseIcon size={20} />
                </IconButton>
              </div>
            ))}

          <Card>
            <div class="fr-code-head">
              <div>
                <span class="fr-code-label">{t('friends_your_code')}</span>
                <span class="fr-code-val" dir="ltr">@{view.me.username}</span>
              </div>
            </div>
            <div class="fr-code-actions">
              <Button variant="tonal" icon={<CopyIcon size={18} />} onClick={() => void handleCopy(view.me.username ?? '')}>
                {t('friends_copy_code')}
              </Button>
              <Button variant="tonal" icon={<ShareOutIcon size={18} />} onClick={() => void handleShare(view.me.username ?? '')}>
                {t('friends_share_code')}
              </Button>
            </div>

            <hr class="fr-div" />

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
                  disabled={state.busy}
                  onInput={(e) => {
                    clearFriendsError()
                    setNameInput((e.target as HTMLInputElement).value)
                  }}
                />
              </div>
              <Button
                type="submit"
                variant="filled"
                disabled={normalizeUsername(nameInput) === null || state.busy}
              >
                {t('friends_add_button')}
              </Button>
            </form>
          </Card>

          {state.error && (
            <p class="fr-err" role="alert">{t('friends_error_line', state.error)}</p>
          )}

          {view.incoming.length > 0 && (
            <>
              <SectionHeader title={t('friends_incoming_title')} />
              <div class="fr-list">
                {view.incoming.map((p) => (
                  <div key={p.id} class="fr-req-row">
                    <Avatar person={p} />
                    <PersonName person={p} grow />
                    <div class="fr-req-actions">
                      <Button variant="tonal" disabled={state.busy} onClick={() => void acceptFriend(p.id)}>
                        {t('friends_accept')}
                      </Button>
                      <Button variant="text" disabled={state.busy} onClick={() => void declineFriend(p.id)}>
                        {t('friends_decline')}
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            </>
          )}

          <SectionHeader
            title={t('friends_board_title')}
            trailing={
              <IconButton
                label={t('friends_refresh')}
                disabled={state.busy}
                onClick={() => void refreshFriends(false)}
              >
                <RefreshIcon />
              </IconButton>
            }
          />

          <div class="fr-list">
            {state.leaderboard.map((entry, idx) => {
              const rowBody = (
                <>
                  <span class="fr-rank">{idx + 1}</span>
                  <Avatar person={entry.person} />
                  <span class="fr-board-mid">
                    <span class="fr-name-line">
                      <span class="fr-name">{entry.person.name}</span>
                      {entry.isMe && <span class="fr-me-pill">{t('friends_me_tag')}</span>}
                    </span>
                    <span class="fr-meta-line">
                      <span class={`fr-badge fr-badge-${entry.badge}`}>{t(BADGE_KEYS[entry.badge])}</span>
                      {entry.syncedThisWeek && (
                        <span class="fr-stats">
                          <span class="fr-stat">
                            <FlameIcon size={14} />
                            <span>{t('friends_stat_streak', entry.streakWeeks)}</span>
                          </span>
                          <span class="fr-stat">
                            <RestaurantIcon size={14} />
                            <span>{t('friends_stat_protein', entry.proteinDays)}</span>
                          </span>
                        </span>
                      )}
                    </span>
                  </span>
                  <span class={entry.syncedThisWeek ? 'fr-score' : 'fr-score fr-score-stale'}>
                    {entry.score}
                  </span>
                </>
              )

              if (entry.isMe) {
                return (
                  <div key={entry.person.id} class="fr-board-row fr-board-me">
                    {rowBody}
                  </div>
                )
              }
              return (
                <button
                  key={entry.person.id}
                  type="button"
                  class="fr-board-row"
                  onClick={() => setSelectedId(entry.person.id)}
                >
                  {rowBody}
                </button>
              )
            })}
          </div>

          {view.friends.length === 0 && (
            <p class="fr-empty-hint">{t('friends_empty_board')}</p>
          )}

          {view.outgoing.length > 0 && (
            <>
              <SectionHeader title={t('friends_outgoing_title')} />
              <div class="fr-list">
                {view.outgoing.map((p) => (
                  <div key={p.id} class="fr-out-row">
                    <Avatar person={p} />
                    <PersonName person={p} />
                  </div>
                ))}
              </div>
            </>
          )}
        </>
      )}

      <Sheet
        open={selectedEntry !== null}
        onClose={() => setSelectedId(null)}
        title={selectedEntry?.person.name ?? ''}
      >
        {selectedEntry && (
          <div class="fr-sheet">
            <div class="fr-sheet-head">
              <span class={`fr-badge fr-badge-${selectedEntry.badge}`}>{t(BADGE_KEYS[selectedEntry.badge])}</span>
              <span class={selectedEntry.syncedThisWeek ? 'fr-score' : 'fr-score fr-score-stale'}>
                {selectedEntry.score}
              </span>
            </div>

            {selectedEntry.syncedThisWeek && (
              <div class="fr-stats">
                <span class="fr-stat">
                  <FlameIcon size={16} />
                  <span>{t('friends_stat_streak', selectedEntry.streakWeeks)}</span>
                </span>
                <span class="fr-stat">
                  <RestaurantIcon size={16} />
                  <span>{t('friends_stat_protein', selectedEntry.proteinDays)}</span>
                </span>
              </div>
            )}

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

            <Button
              variant="tonal"
              block
              disabled={state.nudgedToday.has(selectedEntry.person.id) || state.busy}
              onClick={async () => {
                const res = await nudgeFriend(selectedEntry.person.id)
                if (res.ok) toast(t('friends_nudge_sent'))
              }}
            >
              {state.nudgedToday.has(selectedEntry.person.id) ? t('friends_nudged_today') : t('friends_nudge')}
            </Button>

            <div class="fr-sheet-danger">
              <Button
                variant="text"
                disabled={state.busy}
                onClick={() => setConfirmAction({ kind: 'remove', person: selectedEntry.person })}
              >
                {t('friends_remove')}
              </Button>
              <Button
                variant="text"
                class="kit-danger"
                disabled={state.busy}
                onClick={() => setConfirmAction({ kind: 'block', person: selectedEntry.person })}
              >
                {t('friends_block')}
              </Button>
            </div>
          </div>
        )}
      </Sheet>

      <Dialog
        open={confirmAction !== null}
        title={t(confirmAction?.kind === 'block' ? 'friends_block_confirm_title' : 'friends_remove_confirm_title')}
        message={
          confirmAction
            ? t(
                confirmAction.kind === 'block' ? 'friends_block_confirm_body' : 'friends_remove_confirm_body',
                confirmAction.person.name,
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
          if (target.kind === 'block') void blockFriend(target.person.id)
          else void removeFriend(target.person.id)
        }}
      />
    </div>
  )
}
