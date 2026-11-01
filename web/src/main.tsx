import { render } from 'preact'
import { getLang, t } from './i18n' // sets <html lang dir> before the first paint
import './ui/theme' // sets <html data-theme>
import { App } from './app'
import { requestPersistence } from './data/db'
import { autoBackup, preloadGis } from './data/drive'
import { saveCrash } from './data/crash'
import { loadSettings } from './data/settings'
import { seedLibrary } from './data/workouts'
import { toast } from './ui/kit'
import './styles.css'

// Frame protection: GitHub Pages can't send X-Frame-Options, so a page that frames the app from another site
// gets the app moved to the top window instead, or a hidden app when the browser blocks that. Same-site frames
// (reading top.location throws for any other site) and the normal unframed app are left alone.
function framedByAnotherSite(): boolean {
  if (window.top === window.self) return false
  try { return window.top!.location.origin !== window.location.origin } catch { return true }
}
const framed = framedByAnotherSite()
if (framed) {
  try { window.top!.location.href = window.self.location.href } catch { /* the framing page blocked it */ }
  document.documentElement.style.display = 'none'
}

// Crashes are kept on this phone only (no network); the next launch offers to share the last one (CrashPrompt).
addEventListener('error', (e) => saveCrash(e.error ?? e.message))
addEventListener('unhandledrejection', (e) => { saveCrash(e.reason); toast(t('error_toast')) })

// Built-in exercises (names in the current language) and a request to keep data under storage pressure.
void seedLibrary(getLang())
void requestPersistence()

// Drive: load the sign-in script early (a later tap can then open its popup at once) and back up when stale.
void loadSettings().then((s) => { if (s.driveEmail) { void preloadGis().catch(() => undefined); void autoBackup('open') } })
document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') void autoBackup('open') })

if (!framed) render(<App />, document.getElementById('app')!)
