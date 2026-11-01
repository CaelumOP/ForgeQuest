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

// Crashes are kept on this phone only (no network); the next launch offers to share the last one (CrashPrompt).
addEventListener('error', (e) => saveCrash(e.error ?? e.message))
addEventListener('unhandledrejection', (e) => { saveCrash(e.reason); toast(t('error_toast')) })

// Built-in exercises (names in the current language) and a request to keep data under storage pressure.
void seedLibrary(getLang())
void requestPersistence()

// Drive: load the sign-in script early (a later tap can then open its popup at once) and back up when stale.
void loadSettings().then((s) => { if (s.driveEmail) { void preloadGis().catch(() => undefined); void autoBackup('open') } })
document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') void autoBackup('open') })

render(<App />, document.getElementById('app')!)
