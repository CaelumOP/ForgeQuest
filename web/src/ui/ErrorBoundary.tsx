import { Component, type ComponentChildren } from 'preact'
import { saveCrash } from '../data/crash'
import { t } from '../i18n'
import { Button, Card } from './kit'
import { goBack } from './nav'

type Props = { children?: ComponentChildren }
type State = { error: unknown }

/**
 * Catches a screen that throws while drawing, so one bad record shows a calm card instead of a blank app.
 * App keys it by the route path: going to another screen starts fresh.
 */
export class ScreenBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: unknown): State {
    return { error: error ?? new Error('unknown') }
  }

  componentDidCatch(error: unknown) {
    console.error(error)
    saveCrash(error) // offered as a crash report on the next launch
  }

  render() {
    if (!this.state.error) return this.props.children
    return (
      <div class="content" role="alert">
        <Card>
          <p class="error-card-title">{t('error_screen_title')}</p>
          <p class="muted">{t('error_screen_body')}</p>
          <div class="error-card-actions">
            <Button variant="tonal" onClick={goBack}>{t('error_back')}</Button>
            <Button onClick={() => location.reload()}>{t('error_reload')}</Button>
          </div>
        </Card>
      </div>
    )
  }
}
