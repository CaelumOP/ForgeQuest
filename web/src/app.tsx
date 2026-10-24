import { useEffect, useState } from 'preact/hooks'
import { t, useLang } from './i18n'
import { matchRoute } from './routes'
import { InstallGate, needsInstallGate } from './ui/InstallGate'
import { ToastHost } from './ui/kit'
import { goBack, useRoute } from './ui/nav'
import { NotFoundScreen } from './ui/screens'
import { TabBar } from './ui/TabBar'
import { TopBar } from './ui/TopBar'

const isField = (el: EventTarget | null) => el instanceof HTMLElement && el.matches('input, textarea, select')

/** True while a text field has focus, so the tab bar gets out of the keyboard's way. */
function useFieldFocused() {
  const [focused, setFocused] = useState(false)
  useEffect(() => {
    const onIn = (e: FocusEvent) => setFocused(isField(e.target))
    const onOut = (e: FocusEvent) => setFocused(isField(e.relatedTarget))
    addEventListener('focusin', onIn)
    addEventListener('focusout', onOut)
    return () => { removeEventListener('focusin', onIn); removeEventListener('focusout', onOut) }
  }, [])
  return focused
}

// Decided once per load: an installed app never turns back into a Safari tab.
const gated = needsInstallGate()

export function App() {
  useLang() // re-render everything when the language changes
  const path = useRoute()
  const fieldFocused = useFieldFocused()
  if (gated) return <InstallGate />
  const { route, param } = matchRoute(path)

  if (route?.tab) {
    return (
      <div class="shell">
        <TopBar title={t(route.title)} />
        <main class="scroll"><route.Screen /></main>
        {!fieldFocused && <TabBar route={path} />}
        <ToastHost />
      </div>
    )
  }
  // "bare" screens (live workout, camera) draw their own header.
  if (route?.bare) return <div class="shell"><route.Screen param={param} /><ToastHost /></div>
  return (
    <div class="shell">
      <TopBar title={t(route?.title ?? 'screen_not_found')} onBack={goBack} />
      <div class="scroll">{route ? <route.Screen param={param} /> : <NotFoundScreen />}</div>
      <ToastHost />
    </div>
  )
}
