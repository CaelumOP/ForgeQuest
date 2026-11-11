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
import { CrashPrompt } from './ui/CrashPrompt'
import { ScreenBoundary } from './ui/ErrorBoundary'
import { UpdatePill } from './ui/UpdatePill'
import { TopBar } from './ui/TopBar'
import { quickStart } from './ui/onboarding/OnboardingScreen'
import { Tour, markTourSeen, tourSeen } from './ui/onboarding/Tour'

// Decided once per load: an installed app never turns back into a Safari tab.
const gated = needsInstallGate()
let started = false
const startOnce = () => { if (!started) { started = true; void quickStart().catch((e) => { started = false; console.error('quick start failed', e) }) } }

export function App() {
  useLang() // re-render everything when the language changes
  const path = useRoute()
  const settings = useLive(loadSettings, [], null) // null until the first read, so a returning person never sees a flash of setup
  const [tourOpen, setTourOpen] = useState(() => !tourSeen())
  // The design catalogue (#/design) opens in any browser tab, before install or setup, so it can be browsed anywhere.
  const catalogue = path === '/design'
  if (gated && !catalogue) return <InstallGate />
  if (!settings) return <div class="shell" />
  setHapticsEnabled(settings.hapticsEnabled !== false)
  // Tour only: a first run skips setup and lands on Home; the tour below runs once settings are written.
  if (!settings.onboarded && !catalogue) { startOnce(); return <div class="shell" /> }
  const { route, param } = matchRoute(path)
  const tour = tourOpen && <Tour finishLabel={t('tour_done')} onDone={() => { markTourSeen(); setTourOpen(false) }} />

  if (route?.tab) {
    return (
      <div class="shell">
        <TopBar title={t(route.title)} />
        <main class="scroll"><ScreenBoundary key={path}><route.Screen /></ScreenBoundary></main>
        <CrashPrompt />
        <UpdatePill />
        <BackupPill />
        <TabBar route={path} />
        <ToastHost />
        {tour}
      </div>
    )
  }
  // "bare" screens (live workout, camera) draw their own header.
  if (route?.bare) return <div class="shell"><ScreenBoundary key={path}><route.Screen param={param} /></ScreenBoundary><ToastHost /></div>
  return (
    <div class="shell">
      <TopBar title={t(route?.title ?? 'screen_not_found')} onBack={goBack} />
      <div class="scroll scroll-push" key={path}>{route ? <ScreenBoundary key={path}><route.Screen param={param} /></ScreenBoundary> : <NotFoundScreen />}</div>
      <ToastHost />
    </div>
  )
}
