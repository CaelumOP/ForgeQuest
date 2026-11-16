import type { JSX } from 'preact'
import { useEffect, useRef, useState } from 'preact/hooks'
import {
  changeUsername,
  clearFriendsError,
  deleteFriendsAccount,
  renderGoogleSignInButton,
  shownName,
  signOutFriends,
  updateFriendsAlerts,
  updateFriendsPreferences,
  useFriends,
  type FriendsState,
} from '../../data/friends'
import { enablePush, pushPermission, pushSupported } from '../../data/push'
import { t } from '../../i18n'
import { normalizeUsername } from '../../domain/forge-score'
import { Button, Card, Dialog, TextField, toast } from '../kit'

export function friendsSummary(st: FriendsState): string {
  if (st.status === 'joined' && st.view) {
    return t('settings_friends_status_signed_in', `⁦${shownName(st.view.me)}⁩`)
  }
  if (st.status === 'needs_join') {
    return t('settings_friends_status_pending')
  }
  return t('settings_friends_status_signed_out')
}

export function FriendsSection({
  Title,
}: {
  Title: (p: { title: string; hint?: string }) => JSX.Element
}) {
  const st = useFriends()
  const [confirmDelete, setConfirmDelete] = useState(false)
  const googleRef = useRef<HTMLDivElement>(null)
  const currentUsername = st.view?.me.username ?? ''
  const [username, setUsername] = useState(currentUsername)
  useEffect(() => { setUsername(currentUsername) }, [currentUsername])
  const normalized = normalizeUsername(username)

  const [canPush, setCanPush] = useState(false)
  const [perm, setPerm] = useState(() => pushPermission())
  useEffect(() => {
    void pushSupported().then((ok) => {
      setCanPush(ok)
      setPerm(pushPermission())
    })
  }, [st.status])

  const requestPush = async () => {
    await enablePush()
    setPerm(pushPermission())
  }

  useEffect(() => {
    if (!st.loading && st.status === 'signed_out') {
      void renderGoogleSignInButton(googleRef.current)
    }
  }, [st.loading, st.status])

  return (
    <Card>
      <Title title={t('settings_group_friends')} hint={friendsSummary(st)} />

      {st.status === 'signed_out' && (
        <div class="pf-actions">
          <div ref={googleRef} aria-label={t('settings_friends_sign_in')} />
        </div>
      )}

      {st.status === 'needs_join' && (
        <div class="pf-actions">
          <Button variant="tonal" onClick={() => { location.hash = '#/friends' }}>
            {t('tab_friends')}
          </Button>
          <Button
            variant="text"
            disabled={st.busy}
            onClick={async () => {
              await signOutFriends()
              toast(t('settings_friends_signed_out'))
            }}
          >
            {t('settings_friends_sign_out')}
          </Button>
        </div>
      )}

      {st.status === 'joined' && st.view && (
        <>
          <div class="pf-username">
            <TextField
              label={t('settings_friends_username')}
              hint={t('friends_username_hint')}
              error={username.trim() !== '' && normalized === null ? t('friends_username_rules') : undefined}
              value={username}
              autocomplete="username"
              disabled={st.busy}
              onInput={(v) => { clearFriendsError(); setUsername(v.slice(0, 21)) }}
            />
            <div class="pf-actions">
              <Button
                variant="tonal"
                disabled={st.busy || normalized === null || normalized === currentUsername}
                onClick={async () => {
                  const res = await changeUsername(username)
                  if (res.ok) toast(t('settings_friends_username_saved'))
                }}
              >
                {t('settings_friends_username_save')}
              </Button>
            </div>
          </div>

          <hr class="pf-div" />

          <button
            type="button"
            class="pf-switchrow"
            role="switch"
            aria-checked={st.view.me.sharePhoto}
            disabled={st.busy}
            onClick={() => void updateFriendsPreferences({ sharePhoto: !st.view!.me.sharePhoto })}
          >
            <span class="pf-grow"><span class="pf-t">{t('settings_friends_share_photo')}</span></span>
            <span class={st.view.me.sharePhoto ? 'pf-switch pf-switch-on' : 'pf-switch'} aria-hidden="true">
              <span class="pf-knob" />
            </span>
          </button>

          <hr class="pf-div" />

          <button
            type="button"
            class="pf-switchrow"
            role="switch"
            aria-checked={st.view.me.sharePrs}
            disabled={st.busy}
            onClick={() => void updateFriendsPreferences({ sharePrs: !st.view!.me.sharePrs })}
          >
            <span class="pf-grow"><span class="pf-t">{t('settings_friends_share_prs')}</span></span>
            <span class={st.view.me.sharePrs ? 'pf-switch pf-switch-on' : 'pf-switch'} aria-hidden="true">
              <span class="pf-knob" />
            </span>
          </button>

          <hr class="pf-div" />

          <button
            type="button"
            class="pf-switchrow"
            role="switch"
            aria-checked={st.view.me.alertsOn}
            disabled={st.busy}
            onClick={() => {
              const next = !st.view!.me.alertsOn
              if (next && pushPermission() === 'default') void requestPush()
              void updateFriendsAlerts({ alertsOn: next })
            }}
          >
            <span class="pf-grow"><span class="pf-t">{t('settings_friends_alerts')}</span></span>
            <span class={st.view.me.alertsOn ? 'pf-switch pf-switch-on' : 'pf-switch'} aria-hidden="true">
              <span class="pf-knob" />
            </span>
          </button>

          <hr class="pf-div" />

          <button
            type="button"
            class="pf-switchrow"
            role="switch"
            aria-checked={st.view.me.shareWorkouts}
            disabled={st.busy}
            onClick={() => void updateFriendsAlerts({ shareWorkouts: !st.view!.me.shareWorkouts })}
          >
            <span class="pf-grow">
              <span class="pf-t">{t('settings_friends_share_workouts')}</span>
              <span class="pf-s">{t('settings_friends_share_workouts_hint')}</span>
            </span>
            <span class={st.view.me.shareWorkouts ? 'pf-switch pf-switch-on' : 'pf-switch'} aria-hidden="true">
              <span class="pf-knob" />
            </span>
          </button>

          <hr class="pf-div" />

          <button
            type="button"
            class="pf-switchrow"
            role="switch"
            aria-checked={st.view.me.pingsOn}
            disabled={st.busy}
            onClick={() => {
              const next = !st.view!.me.pingsOn
              if (next && pushPermission() === 'default') void requestPush()
              void updateFriendsAlerts({ pingsOn: next })
            }}
          >
            <span class="pf-grow"><span class="pf-t">{t('settings_friends_pings')}</span></span>
            <span class={st.view.me.pingsOn ? 'pf-switch pf-switch-on' : 'pf-switch'} aria-hidden="true">
              <span class="pf-knob" />
            </span>
          </button>

          {canPush && perm === 'default' && (
            <>
              <hr class="pf-div" />
              <div class="pf-setting">
                <span class="pf-grow"><span class="pf-t">{t('friends_turn_on_notifs')}</span></span>
                <Button variant="tonal" onClick={() => void requestPush()}>
                  {t('friends_turn_on_notifs_btn')}
                </Button>
              </div>
            </>
          )}

          <div class="pf-actions">
            <Button
              variant="tonal"
              disabled={st.busy}
              onClick={async () => {
                await signOutFriends()
                toast(t('settings_friends_signed_out'))
              }}
            >
              {t('settings_friends_sign_out')}
            </Button>
            <Button
              variant="text"
              class="kit-danger"
              disabled={st.busy}
              onClick={() => setConfirmDelete(true)}
            >
              {t('settings_friends_delete_account')}
            </Button>
          </div>
        </>
      )}

      {st.error && <p class="pf-s" role="alert">{t('friends_error_line', st.error)}</p>}

      <Dialog
        open={confirmDelete}
        title={t('settings_friends_delete_title')}
        message={t('settings_friends_delete_body')}
        confirmLabel={t('settings_friends_delete_confirm')}
        cancelLabel={t('friends_cancel')}
        destructive
        onCancel={() => setConfirmDelete(false)}
        onConfirm={async () => {
          setConfirmDelete(false)
          const res = await deleteFriendsAccount()
          if (res.ok) toast(t('settings_friends_deleted'))
        }}
      />
    </Card>
  )
}
