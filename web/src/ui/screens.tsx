import { t } from '../i18n'
import { Card } from './kit'
import { EXERCISE_MEDIA_CREDITS } from '../data/seed/exercise-media-credits'

export function AboutScreen() {
  return (
    <div class="content">
      <p>{t('about_body')}</p>
      <p class="muted">{t('profile_version', __APP_VERSION__)}</p>
      <Card>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <h3 class="pf-h3">{t('profile_credits')}</h3>
          <p class="muted">{t('profile_credits_body')}</p>
          <p class="muted">{t('profile_credits_data')}</p>
          <p class="muted">{t('profile_credits_images')}</p>
          <ul class="muted" dir="ltr" style={{ margin: 0, paddingInlineStart: '18px', fontSize: '0.85em' }}>
            {EXERCISE_MEDIA_CREDITS.map((c) => (
              <li key={c.exercise}>
                {c.exercise}: <a class="pf-link" href={c.url} target="_blank" rel="noopener noreferrer">{c.line}</a>
              </li>
            ))}
          </ul>
          <div>
            <a class="pf-link" href="https://www.youtube.com/@HemoCalisthenics" target="_blank" rel="noopener noreferrer">
              {t('profile_hemo_youtube')}
            </a>
          </div>
        </div>
      </Card>
    </div>
  )
}

export const NotFoundScreen = () => <div class="content" />
