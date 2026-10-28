import { useState } from 'preact/hooks'
import { t, useLang } from './i18n'
import { loadSettings } from './data/settings'
import { useLive } from './data/live'
import { matchRoute } from './routes'
import { InstallGate, needsInstallGate } from './ui/InstallGate'
import { ToastHost, setHapticsEnabled } from './ui/kit'
import { goBack, useRoute } from './ui/nav'
import { NotFoundScreen } from './ui/screens'
import { TabBar } from './ui/TabBar'
import { BackupPill } from './ui/BackupPill'
import { UpdatePill } from './ui/UpdatePill'
import { TopBar } from './ui/TopBar'
import { OnboardingScreen } from './ui/onboarding/OnboardingScreen'
import { Tour, markTourSeen, tourSeen } from './ui/onboarding/Tour'

// Decided once per load: an installed app never turns back into a Safari tab.
const gated = needsInstallGate()

export function App() {
  useLang() // re-render everything when the language changes
  const path = useRoute()
  const settings = useLive(loadSettings, [], null) // null until the first read, so a returning person never sees a flash of setup
  const [tourOpen, setTourOpen] = useState(() => !tourSeen())
  if (gated) return <InstallGate />
  if (!settings) return <div class="shell" />
  setHapticsEnabled(settings.hapticsEnabled !== false)
  if (!settings.onboarded) return <><OnboardingScreen onDone={() => undefined} /><ToastHost /></>
  const { route, param } = matchRoute(path)
  const tour = tourOpen && <Tour finishLabel={t('tour_done')} onDone={() => { markTourSeen(); setTourOpen(false) }} />

  if (route?.tab) {
    return (
      <div class="shell">
        <TopBar title={t(route.title)} />
        <main class="scroll"><route.Screen /></main>
        <UpdatePill />
        <BackupPill />
        <TabBar route={path} />
        <ToastHost />
        {tour}
      </div>
    )
  }
  // "bare" screens (live workout, camera) draw their own header.
  if (route?.bare) return <div class="shell"><route.Screen param={param} /><ToastHost /></div>
  return (
    <div class="shell">
      <TopBar title={t(route?.title ?? 'screen_not_found')} onBack={goBack} />
      <div class="scroll scroll-push" key={path}>{route ? <route.Screen param={param} /> : <NotFoundScreen />}</div>
      <ToastHost />
    </div>
  )
}
