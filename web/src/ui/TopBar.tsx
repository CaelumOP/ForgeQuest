import { t } from '../i18n'
import { BackChevron } from './icons'

/** Screen header. Pushed screens pass onBack to get a Back button at the start side. */
export function TopBar({ title, onBack }: { title: string; onBack?: () => void }) {
  return (
    <header class="top-bar">
      {onBack && (
        <button type="button" class="icon-button back" aria-label={t('action_back')} onClick={onBack}>
          <BackChevron />
        </button>
      )}
      <h1 class="top-bar-title">{title}</h1>
    </header>
  )
}
