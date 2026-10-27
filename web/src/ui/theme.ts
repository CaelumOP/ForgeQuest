import { useEffect, useState } from 'preact/hooks'

export type Theme = 'system' | 'light' | 'dark'

const KEY = 'fq.theme'
const listeners = new Set<(t: Theme) => void>()

function initialTheme(): Theme {
  try {
    const saved = localStorage.getItem(KEY)
    if (saved === 'system' || saved === 'light' || saved === 'dark') return saved
  } catch { /* storage blocked: follow the phone */ }
  return 'system'
}

let theme: Theme = initialTheme()

function applyTheme() {
  if (typeof document === 'undefined') return
  document.documentElement.dataset.theme = theme
}
applyTheme()

export function setTheme(next: Theme) {
  theme = next
  try { localStorage.setItem(KEY, next) } catch { /* applies for this visit only */ }
  applyTheme()
  listeners.forEach((fn) => fn(next))
}

export function useTheme(): Theme {
  const [value, setValue] = useState(theme)
  useEffect(() => {
    listeners.add(setValue)
    setValue(theme)
    return () => { listeners.delete(setValue) }
  }, [])
  return value
}
