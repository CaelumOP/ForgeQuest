import { t } from '../i18n'
import { TabIcon, type TabIconName } from './icons'

export const tabs: { route: string; label: string; icon: TabIconName }[] = [
  { route: '/', label: 'tab_home', icon: 'home' },
  { route: '/workouts', label: 'tab_workouts', icon: 'fitness' },
  { route: '/food', label: 'tab_food', icon: 'restaurant' },
  { route: '/coach', label: 'tab_coach', icon: 'sparkle' },
  { route: '/profile', label: 'tab_profile', icon: 'account' },
]

/** Flex row, so the order follows the reading direction (Home sits at the right in Arabic). */
export function TabBar({ route }: { route: string }) {
  return (
    <nav class="tab-bar" aria-label={t('nav_main')}>
      {tabs.map((tab) => {
        const selected = tab.route === route
        return (
          <a key={tab.route} class="tab" href={`#${tab.route}`} aria-current={selected ? 'page' : undefined}>
            <span class="tab-icon"><TabIcon name={tab.icon} filled={selected} /></span>
            <span class="tab-label">{t(tab.label)}</span>
          </a>
        )
      })}
    </nav>
  )
}
