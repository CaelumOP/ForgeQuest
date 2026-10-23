import { useEffect, useState } from 'preact/hooks'
import { AiCheck } from './dev/AiCheck'
import { t, useLang } from './i18n'
import { InstallGate, needsInstallGate } from './ui/InstallGate'
import { goBack, useRoute } from './ui/nav'
import { TabBar } from './ui/TabBar'
import { TopBar } from './ui/TopBar'
import { AboutScreen, CoachScreen, FoodScreen, HomeScreen, NotFoundScreen, ProfileScreen, WorkoutsScreen } from './ui/screens'

const tabScreens: Record<string, { title: string; Screen: () => preact.JSX.Element }> = {
  '/': { title: 'tab_home', Screen: HomeScreen },
  '/workouts': { title: 'tab_workouts', Screen: WorkoutsScreen },
  '/food': { title: 'tab_food', Screen: FoodScreen },
  '/coach': { title: 'tab_coach', Screen: CoachScreen },
  '/profile': { title: 'tab_profile', Screen: ProfileScreen },
}

const pushedScreens: Record<string, { title: string; Screen: () => preact.JSX.Element }> = {
  '/about': { title: 'settings_group_about', Screen: AboutScreen },
  '/check': { title: 'screen_ai_check', Screen: AiCheck },
}

const isField = (el: EventTarget | null) => el instanceof HTMLElement && el.matches('input, textarea, select')

/** True while a text field has focus, so the tab bar gets out of the keyboard's way. */
function useFieldFocused() {
  const [focused, setFocused] = useState(false)
  useEffect(() => {
    const onIn = (e: FocusEvent) => setFocused(isField(e.target))
    const onOut = (e: FocusEvent) => setFocused(isField(e.relatedTarget))
    addEventListener('focusin', onIn)
    addEventListener('focusout', onOut)
    return () => {
      removeEventListener('focusin', onIn)
      removeEventListener('focusout', onOut)
    }
  }, [])
  return focused
}

// Decided once per load: an installed app never turns back into a Safari tab.
const gated = needsInstallGate()

export function App() {
  useLang() // re-render everything when the language changes
  const route = useRoute()
  const fieldFocused = useFieldFocused()
  if (gated) return <InstallGate />

  const tab = tabScreens[route]
  if (tab) {
    return (
      <div class="shell">
        <TopBar title={t(tab.title)} />
        <main class="scroll"><tab.Screen /></main>
        {!fieldFocused && <TabBar route={route} />}
      </div>
    )
  }
  const pushed = pushedScreens[route]
  const Scroll = route === '/check' ? 'div' : 'main' // AiCheck brings its own <main>
  return (
    <div class="shell">
      <TopBar title={t(pushed?.title ?? 'screen_not_found')} onBack={goBack} />
      <Scroll class="scroll">{pushed ? <pushed.Screen /> : <NotFoundScreen />}</Scroll>
    </div>
  )
}
