#!/usr/bin/env node
/**
 * Offline audit for every exercise cue in EN and AR across Android string XMLs and Web i18n JSONs.
 * Usage: node tools/catalog/audit-cues.mjs
 */
import { readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

export const DANGLING_EN_ENDINGS = [
  'a', 'an', 'the',
  'of', 'in', 'on', 'at', 'to', 'for', 'with', 'from', 'by',
  'into', 'onto', 'upon', 'during', 'underneath', 'among', 'against',
  'toward', 'towards', 'until', 'till', 'within', 'than', 'as', 'via', 'per',
  'beside', 'between',
  'and', 'or', 'but', 'nor',
  'if', 'when', 'while', 'where', 'that', 'which', 'who', 'whom', 'whose',
  'your', 'my', 'his', 'her', 'its', 'our', 'their',
  'these', 'those', 'some', 'any', 'every', 'each', 'either', 'neither', 'such',
  'is', 'are', 'was', 'were', 'be', 'been', 'being',
  'have', 'has', 'had', 'does', 'did',
  'will', 'would', 'shall', 'should', 'could', 'may', 'might', 'must',
  'not', 'very', 'quite', 'non-working',
]

// Valid phrasal/idiomatic endings in English cues where a word in DANGLING_EN_ENDINGS can legally end a sentence.
const VALID_EN_PHRASAL_ENDINGS = [
  /\b(cave|breathe|elbows|facing|tuck|tucked|pull|pulls|pulling|pulled|sink|sinks)\s+in$/i,
]

const CONTEXT_DANGLING_EN = [
  { re: /\byour\s+starting$/i, reason: 'truncated_phrase:your starting' },
  { re: /\bthis$/i, allow: /\b(do|doing|like)\s+this$/i, reason: 'dangling_en:this' },
  { re: /\bcan$/i, allow: /\bas\b.*\byou\s+can$/i, reason: 'dangling_en:can' },
  { re: /\bover$/i, allow: /\b(chin|take|roll|rolls|rolling|tip|tips)\s+over$/i, reason: 'dangling_en:over' },
  { re: /\bunder$/i, allow: /\bpelvis\b.*\bunder$/i, reason: 'dangling_en:under' },
]

export const DANGLING_AR_ENDINGS = [
  'في', 'من', 'إلى', 'الى', 'على', 'عن', 'مع', 'بين', 'حتى', 'عند',
  'نحو', 'تجاه', 'ضد', 'دون', 'بدون', 'قبل', 'بعد', 'أثناء', 'خلال',
  'حول', 'فوق', 'تحت', 'أمام', 'خلف', 'بجانب', 'وراء',
  'و', 'أو', 'ثم', 'لكن', 'بل',
  'أن', 'إن', 'كي', 'لكي', 'لأن', 'حيث', 'إذا', 'لو', 'عندما', 'بينما', 'كما', 'مثل',
  'كل', 'بعض', 'جميع', 'غير', 'سوى',
  'هذا', 'هذه', 'ذلك', 'تلك', 'التي', 'الذي', 'الذين',
  'ما', 'لا', 'لم', 'لن', 'قد', 'سوف',
  'كان', 'تكون', 'يكون', 'يجب', 'يمكن', 'عدم', 'ال',
]

const DANGLING_EN_SET = new Set(DANGLING_EN_ENDINGS)
const DANGLING_AR_SET = new Set(DANGLING_AR_ENDINGS)

function decodeXmlEntities(raw) {
  return raw
    .replace(/\\'/g, "'")
    .replace(/\\"/g, '"')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
}

export function parseCueXml(xmlPath) {
  const xml = readFileSync(xmlPath, 'utf8')
  const out = new Map()
  const re = /<string\s+name="(cue_[a-z0-9_]+)"\s*>([\s\S]*?)<\/string>/g
  let m
  while ((m = re.exec(xml)) !== null) {
    out.set(m[1], decodeXmlEntities(m[2].trim()))
  }
  return out
}

export function auditCue(key, text, lang) {
  const issues = []
  if (typeof text !== 'string' || !text.trim()) {
    return ['empty']
  }
  const s = text.trim()
  if (text !== s) issues.push('leading_or_trailing_whitespace')
  if (s.includes('\uFFFD')) issues.push('replacement_char')
  if (s.includes('...') || s.includes('…')) issues.push('ellipsis')

  const quoteCount = (s.match(/"/g) ?? []).length
  if (quoteCount % 2 !== 0) issues.push('unmatched_double_quote')
  const openParens = (s.match(/\(/g) ?? []).length
  const closeParens = (s.match(/\)/g) ?? []).length
  if (openParens !== closeParens) issues.push('unmatched_parenthesis')
  const openGuill = (s.match(/«/g) ?? []).length
  const closeGuill = (s.match(/»/g) ?? []).length
  if (openGuill !== closeGuill) issues.push('unmatched_guillemet')

  if (/(\.\.|,\.|;\.|:\.|،\.)/.test(s)) issues.push('broken_punctuation')
  if (!/[.!?»)"]$/.test(s)) issues.push('missing_terminal_punctuation')
  if (/\.\s+\d+$/.test(s)) issues.push('trailing_digit_artifact')

  const clean = s.replace(/[.!?,;:"'»)]+$/u, '').trim()
  const words = clean.split(/\s+/).filter(Boolean)
  const lastWord = words.at(-1) ?? ''

  if (lang === 'en') {
    const lw = lastWord.toLowerCase()
    if (DANGLING_EN_SET.has(lw) && !VALID_EN_PHRASAL_ENDINGS.some((re) => re.test(clean))) {
      issues.push(`dangling_en:${lw}`)
    }
    for (const rule of CONTEXT_DANGLING_EN) {
      if (rule.re.test(clean) && (!rule.allow || !rule.allow.test(clean))) {
        issues.push(rule.reason)
      }
    }
  } else {
    if (s.length >= 90) issues.push(`ar_too_long:${s.length}`)
    if (/[A-Za-z]/.test(s)) issues.push('latin_in_ar')
    if (lastWord.endsWith('ـ')) issues.push('cut_ar_word')
    if (DANGLING_AR_SET.has(lastWord)) issues.push(`dangling_ar:${lastWord}`)
  }

  return issues
}

export function auditAllCues(repoRoot) {
  const androidEn = new Map([
    ...parseCueXml(join(repoRoot, 'app/src/main/res/values/strings_cues.xml')),
    ...parseCueXml(join(repoRoot, 'app/src/main/res/values/strings_catalog.xml')),
  ])
  const androidAr = new Map([
    ...parseCueXml(join(repoRoot, 'app/src/main/res/values-ar/strings_cues.xml')),
    ...parseCueXml(join(repoRoot, 'app/src/main/res/values-ar/strings_catalog.xml')),
  ])

  const webCuesSrc = readFileSync(join(repoRoot, 'web/src/data/seed/exercise-cues.ts'), 'utf8')
  const webEn = JSON.parse(readFileSync(join(repoRoot, 'web/src/i18n/en.json'), 'utf8'))
  const webAr = JSON.parse(readFileSync(join(repoRoot, 'web/src/i18n/ar.json'), 'utf8'))

  const issues = []
  const cueKeysInWeb = [...webCuesSrc.matchAll(/'(cue_[a-z0-9_]+)'/g)].map((m) => m[1])
  const exerciseCount = [...webCuesSrc.matchAll(/^\s+'[^']+':\s*\[/gm)].length

  for (const key of cueKeysInWeb) {
    if (!androidEn.has(key)) issues.push({ lang: 'en', source: 'android', key, reasons: ['missing_key'], text: '' })
    if (!androidAr.has(key)) issues.push({ lang: 'ar', source: 'android', key, reasons: ['missing_key'], text: '' })
    if (typeof webEn[key] !== 'string') issues.push({ lang: 'en', source: 'web', key, reasons: ['missing_key'], text: '' })
    if (typeof webAr[key] !== 'string') issues.push({ lang: 'ar', source: 'web', key, reasons: ['missing_key'], text: '' })
    if (androidEn.has(key) && webEn[key] !== androidEn.get(key)) {
      issues.push({ lang: 'en', source: 'sync', key, reasons: ['android_web_mismatch'], text: webEn[key] })
    }
    if (androidAr.has(key) && webAr[key] !== androidAr.get(key)) {
      issues.push({ lang: 'ar', source: 'sync', key, reasons: ['android_web_mismatch'], text: webAr[key] })
    }
  }

  for (const [key, text] of androidEn) {
    const reasons = auditCue(key, text, 'en')
    if (reasons.length) issues.push({ lang: 'en', source: 'android', key, reasons, text })
  }
  for (const [key, text] of androidAr) {
    const reasons = auditCue(key, text, 'ar')
    if (reasons.length) issues.push({ lang: 'ar', source: 'android', key, reasons, text })
  }

  return {
    exercises: exerciseCount,
    checkedEn: androidEn.size,
    checkedAr: androidAr.size,
    totalCues: androidEn.size + androidAr.size,
    issues,
  }
}

const isMain = process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href
if (isMain) {
  const here = dirname(fileURLToPath(import.meta.url))
  const repoRoot = resolve(here, '..', '..')
  const result = auditAllCues(repoRoot)
  console.log(
    `Checked ${result.checkedEn} EN + ${result.checkedAr} AR cues (${result.exercises} exercises, ${result.totalCues} strings) across Android XML and Web i18n.`,
  )
  if (result.issues.length > 0) {
    for (const hit of result.issues) {
      console.error(`[${hit.lang.toUpperCase()}:${hit.source}] ${hit.key} (${hit.reasons.join(', ')}): ${hit.text}`)
    }
    console.error(`FAIL: ${result.issues.length} cue issue(s) found.`)
    process.exit(1)
  }
  console.log('OK: 0 truncated or broken cues found.')
}
