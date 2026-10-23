import { render } from 'preact'
import '@fontsource/ibm-plex-sans-arabic/arabic-400.css'
import '@fontsource/ibm-plex-sans-arabic/arabic-600.css'
import './i18n' // sets <html lang dir> before the first paint
import './ui/theme' // sets <html data-theme>
import { App } from './app'
import './styles.css'

render(<App />, document.getElementById('app')!)
