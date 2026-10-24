import { render } from 'preact'
import '@fontsource/ibm-plex-sans-arabic/arabic-400.css'
import '@fontsource/ibm-plex-sans-arabic/arabic-600.css'
import { getLang } from './i18n' // sets <html lang dir> before the first paint
import './ui/theme' // sets <html data-theme>
import { App } from './app'
import { requestPersistence } from './data/db'
import { seedLibrary } from './data/workouts'
import './styles.css'

// Built-in exercises (names in the current language) and a request to keep data under storage pressure.
void seedLibrary(getLang())
void requestPersistence()

render(<App />, document.getElementById('app')!)
