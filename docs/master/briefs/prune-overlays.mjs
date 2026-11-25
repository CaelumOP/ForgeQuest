import { readFileSync, writeFileSync, readdirSync } from 'node:fs'
const dir = 'web/src/i18n/web/'
const gen = { en: JSON.parse(readFileSync('web/src/i18n/en.json', 'utf8')), ar: JSON.parse(readFileSync('web/src/i18n/ar.json', 'utf8')) }
let removed = 0; const differ = []
for (const f of readdirSync(dir).filter((f) => /\.(en|ar)\.json$/.test(f))) {
  const lang = f.endsWith('.ar.json') ? 'ar' : 'en'
  const raw = readFileSync(dir + f, 'utf8'); const o = JSON.parse(raw); let changed = false
  for (const k of Object.keys(o)) {
    if (!(k in gen[lang])) continue
    if (gen[lang][k] === o[k]) { delete o[k]; removed++; changed = true } else differ.push(`${f}:${k}`)
  }
  if (changed) writeFileSync(dir + f, JSON.stringify(o, null, 2) + '\n', 'utf8')
}
console.log(`removed ${removed}; differ ${differ.length}: ${differ.slice(0, 20).join(' ')}`)
