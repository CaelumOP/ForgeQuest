import { useEffect, useState } from 'preact/hooks'
import en from './en.json'
import ar from './ar.json'

export type Lang = 'en' | 'ar'
type Entry = string | Record<string, string>
type Table = Record<string, Entry>

// Web-only strings: one file pair per feature in ./web/<feature>.<lang>.json, so workers never share a file.
const webFiles = import.meta.glob<Table>('./web/*.json', { eager: true, import: 'default' })
function webTable(lang: Lang): Table {
  return Object.assign({}, ...Object.entries(webFiles).filter(([p]) => p.endsWith(`.${lang}.json`)).map(([, t]) => t))
}

const tables: Record<Lang, Table> = {
  en: { ...(en as Table), ...webTable('en') },
  ar: { ...(ar as Table), ...webTable('ar') },
}

const KEY = 'fq.lang'
const listeners = new Set<(l: Lang) => void>()

function initialLang(): Lang {
  try {
    const saved = localStorage.getItem(KEY)
    if (saved === 'en' || saved === 'ar') return saved
  } catch { /* storage blocked: fall back to the phone setting */ }
  return typeof navigator !== 'undefined' && navigator.language?.toLowerCase().startsWith('ar') ? 'ar' : 'en'
}

let lang: Lang = initialLang()

function applyLang() {
  if (typeof document === 'undefined') return
  document.documentElement.lang = lang
  document.documentElement.dir = lang === 'ar' ? 'rtl' : 'ltr'
}
applyLang()

export const getLang = (): Lang => lang

export function setLang(next: Lang) {
  lang = next
  try { localStorage.setItem(KEY, next) } catch { /* not saved; still applies for this visit */ }
  applyLang()
  listeners.forEach((fn) => fn(next))
}

/** Re-renders the calling component when the language changes. */
export function useLang(): Lang {
  const [value, setValue] = useState(lang)
  useEffect(() => {
    listeners.add(setValue)
    setValue(lang)
    return () => { listeners.delete(setValue) }
  }, [])
  return value
}

/** The entry and the language it came from (missing Arabic falls back to English). */
function find(key: string): [Entry, Lang] | undefined {
  const own = tables[lang][key]
  if (own !== undefined) return [own, lang]
  const fallback = tables.en[key]
  return fallback !== undefined ? [fallback, 'en'] : undefined
}

function fill(text: string, args: unknown[]): string {
  return text.replace(/\{(\d+)\}/g, (m, n) => (Number(n) - 1 < args.length ? String(args[Number(n) - 1]) : m))
}

export function t(key: string, ...args: unknown[]): string {
  const hit = find(key)
  if (!hit) return key
  const [entry] = hit
  return fill(typeof entry === 'string' ? entry : entry.other ?? key, args)
}

/** Android getQuantityString: picks the plural category for `count`. With no args, {1} is the count. */
export function plural(key: string, count: number, ...args: unknown[]): string {
  const hit = find(key)
  if (!hit) return key
  const [entry, from] = hit
  const fillArgs = args.length ? args : [count]
  if (typeof entry === 'string') return fill(entry, fillArgs)
  const category = new Intl.PluralRules(from).select(count)
  return fill(entry[category] ?? entry.other ?? key, fillArgs)
}
