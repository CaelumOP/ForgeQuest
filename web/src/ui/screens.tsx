import type { ComponentChildren } from 'preact'
import { setLang, t, useLang, type Lang } from '../i18n'
import { setTheme, useTheme, type Theme } from './theme'
import { BackChevron } from './icons'

// Placeholders until the real screens are ported.

function Placeholder({ children }: { children?: ComponentChildren }) {
  return (
    <div class="content">
      <p class="muted">{t('placeholder_soon')}</p>
      {children}
    </div>
  )
}

export const HomeScreen = () => <Placeholder />
export const WorkoutsScreen = () => <Placeholder />
export const FoodScreen = () => <Placeholder />
export const CoachScreen = () => <Placeholder />

export function ProfileScreen() {
  const lang = useLang()
  const theme = useTheme()
  return (
    <Placeholder>
      <label class="field">
        {t('profile_language')}
        <select value={lang} onChange={(e) => setLang(e.currentTarget.value as Lang)}>
          <option value="en" lang="en">English</option>
          <option value="ar" lang="ar">العربية</option>
        </select>
      </label>
      <label class="field">
        {t('profile_theme')}
        <select value={theme} onChange={(e) => setTheme(e.currentTarget.value as Theme)}>
          <option value="system">{t('theme_system')}</option>
          <option value="light">{t('theme_light')}</option>
          <option value="dark">{t('theme_dark')}</option>
        </select>
      </label>
      <a class="row-link" href="#/about">
        <span>{t('settings_group_about')}</span>
        <BackChevron class="forward" size={18} />
      </a>
    </Placeholder>
  )
}

export function AboutScreen() {
  return (
    <div class="content">
      <p>{t('about_body')}</p>
      <p class="muted">{t('profile_credits_body')}</p>
    </div>
  )
}

export const NotFoundScreen = () => <div class="content" />
