/// <reference types="vite-plugin-pwa/client" />
// "Update ready": a new version was downloaded in the background. Shown above the tab bar (never on the live
// workout or camera screens, which have no tab bar); one tap switches to it. Home Screen apps can stay open
// for days, so it also checks for a new version whenever the app comes back to the front.
import { useEffect, useState } from 'preact/hooks'
import { registerSW } from 'virtual:pwa-register'
import { t } from '../i18n'

let ready = false
const listeners = new Set<() => void>()

const updateSW = registerSW({
  immediate: true,
  onNeedRefresh() { ready = true; listeners.forEach((l) => l()) },
  onRegisteredSW(_url, reg) {
    if (!reg) return
    document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') void reg.update().catch(() => undefined) })
  },
})

export function UpdatePill() {
  const [show, setShow] = useState(ready)
  useEffect(() => {
    const l = () => setShow(true)
    listeners.add(l)
    return () => { listeners.delete(l) }
  }, [])
  if (!show) return null
  return <button type="button" class="backup-pill" onClick={() => void updateSW(true)}>{t('update_web_ready')}</button>
}
