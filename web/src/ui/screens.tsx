import { t } from '../i18n'

export function AboutScreen() {
  return (
    <div class="content">
      <p>{t('about_body')}</p>
      <p class="muted">{t('profile_credits_body')}</p>
    </div>
  )
}

export const NotFoundScreen = () => <div class="content" />
