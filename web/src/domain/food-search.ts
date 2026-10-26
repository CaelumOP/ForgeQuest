// Port of app/.../domain/FoodSearch.kt: forgiving search over the person's own saved foods and restaurants (nothing
// from outside). Arabic spelling variants (أ/ا، ة/ه، ى/ي), tashkeel, vowel letters (كفتيريا = كافتيرا), Arabic and English
// by sound (شاورما = shawarma), typos and words typed together or apart (كوكا كولا = كوكاكولا).
// Both apps test against app/src/test/resources/food_search_cases.json.

/** Lowercase, no tashkeel or accents, one form per Arabic letter, Western digits, words split by single spaces. */
export function normalize(s: string): string {
  let out = ''
  // أ إ آ ؤ ئ lose their hamza/madda in NFD.
  for (const ch of s.toLowerCase().normalize('NFD').replace(/\p{Mn}+/gu, '')) {
    const code = ch.charCodeAt(0)
    if (ch === 'ٱ') out += 'ا'
    else if (ch === 'ة') out += 'ه'
    else if (ch === 'ى' || ch === 'ی' || ch === 'ې') out += 'ي'
    else if (ch === 'ک') out += 'ك'
    else if ('ءـ\'’`'.includes(ch)) continue // Barn's = barns
    else if (code >= 0x660 && code <= 0x669) out += String(code - 0x660)
    else if (code >= 0x6f0 && code <= 0x6f9) out += String(code - 0x6f0)
    else out += ch
  }
  return out.replace(/[^\p{L}\p{N}]+/gu, ' ').trim()
}

const stripAl = (w: string) => (w.length > 4 ? w.replace(/^وال/, '').replace(/^ال/, '') : w)

/** Words of `s`, without a leading "ال" on longer words; one-letter words (like a lone "و") are left out. */
export const words = (s: string): string[] => normalize(s).split(' ').filter((w) => w.length > 1).map(stripAl)

/** `w` and, when it starts with the "و" of "and", the word without it (افراح وتهاني = افراح و تهاني). */
const variants = (w: string): string[] => (w.length > 3 && w.startsWith('و') ? [w, w.slice(1)] : [w])

const ARABIC_SOUND: Record<string, string> = {
  ب: 'b', ت: 't', ث: 's', ج: 'g', ح: 'h', خ: 'k', د: 'd', ذ: 'z', ر: 'r', ز: 'z', س: 's', ش: 'x', ص: 's', ض: 'd', ط: 't',
  ظ: 'z', ع: '', غ: 'g', ف: 'f', ق: 'k', ك: 'k', ل: 'l', م: 'm', ن: 'n', ه: 'h', و: 'w', ي: 'y', ا: '',
}

/** The consonant sounds of one word, the same for Arabic and English spellings (كفتيريا = cafeteria = kftr). */
export function soundKey(w: string): string {
  let s: string
  if (/[a-z]/.test(w)) {
    // Double letters are one sound in English spelling (latte, coffee); Arabic writes them once anyway.
    s = w.replace(/(.)\1+/g, '$1')
      .replace(/sh|ch/g, 'x').replace(/kh/g, 'k').replace(/gh/g, 'g').replace(/th/g, 's').replace(/ph/g, 'f')
      .replace(/ck|q|c/g, 'k').replace(/v/g, 'f').replace(/p/g, 'b').replace(/j/g, 'g')
  } else {
    // A final ه/ة is a vowel: كبسة = kabsa.
    s = [...w.replace(/ه$/, '')].map((ch) => ARABIC_SOUND[ch] ?? ch).join('')
  }
  s = s.replace(/z/g, 's') // بارنز = barns
  if (!s) return ''
  // Keep the first letter; drop vowels and weak letters after it.
  return (s[0] + s.slice(1).replace(/[aeiouwy]/g, '')).replace(/^[aeiou]+/, '')
}

function typoDistance(a: string, b: string): number {
  const d = Array.from({ length: a.length + 1 }, (_, i) => Array.from({ length: b.length + 1 }, (_, j) => (i === 0 ? j : j === 0 ? i : 0)))
  for (let i = 1; i <= a.length; i++) {
    for (let j = 1; j <= b.length; j++) {
      d[i][j] = Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1))
      if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1)
    }
  }
  return d[a.length][b.length]
}

const typosAllowed = (length: number) => (length <= 3 ? 0 : length <= 6 ? 1 : 2)

/**
 * How well one typed word matches one saved word: 1 the same, .9 starts with it, .8 sounds the same, .75 inside it,
 * .7 sound starts with it, .65 a short sound between longer words (latte = لاتيه), .6 a typo or two, 0 no match.
 */
function wordScore(q: string, w: string): number {
  if (q === w) return 1
  if (q.length >= 2 && w.startsWith(q)) return 0.9
  const kq = soundKey(q)
  const kw = soundKey(w)
  if (q.length >= 3 && kq.length >= 3 && kq === kw) return 0.8
  if (q.length >= 3 && w.includes(q)) return 0.75
  if (q.length >= 3 && kq.length >= 3 && kw.startsWith(kq)) return 0.7
  if (q.length >= 4 && w.length >= 4 && kq.length === 2 && kq === kw) return 0.65
  if (typoDistance(q, w) <= typosAllowed(q.length)) return 0.6
  return 0
}

/**
 * How well `query` matches a food: every typed word must match a word of its name or restaurant (0 otherwise); the
 * score is their average. Words typed apart can match a name written as one word, and the other way round.
 */
export function score(query: string, name: string, place = ''): number {
  const nameWords = words(name)
  const saved = [...nameWords, ...words(place)].flatMap(variants)
  saved.push(nameWords.join(''))
  const typed = words(query)
  if (!typed.length) return 0
  const all = (list: string[]): number => {
    let total = 0
    for (const q of list) {
      const best = Math.max(...variants(q).map((v) => Math.max(0, ...saved.map((w) => wordScore(v, w)))))
      if (best === 0) return 0
      total += best
    }
    return total / list.length
  }
  return Math.max(all(typed), typed.length > 1 ? all([typed.join('')]) : 0)
}

/** Venue words: they say what kind of place it is, not which one, so they don't count when comparing restaurants. */
const VENUE_WORDS = new Set([
  'مطعم', 'مطاعم', 'كفتيريا', 'كافيه', 'كوفي', 'مقهى', 'شاورما', 'بروستد', 'مطبخ', 'بوفيه', 'مخبز', 'مخابز', 'حلويات',
  'فطائر', 'مشويات', 'restaurant', 'cafe', 'coffee', 'cafeteria', 'kitchen', 'grill', 'bakery', 'shawarma', 'broast',
].map((w) => soundKey(words(w)[0])))

const placeKeys = (p: string): Set<string> => new Set(words(p).map((w) => soundKey(variants(w).at(-1)!)).filter(Boolean))

/**
 * How alike two restaurant names are, 0 to 1. Venue words count for nothing; other words count more the rarer they
 * are among `allPlaces` (the person's restaurants). شاورما افراح وتهاني and كفتيريا افراح وتهاني come out at 1.
 */
export function placeSimilarity(a: string, b: string, allPlaces: readonly string[]): number {
  if (normalize(a).replace(/ /g, '') === normalize(b).replace(/ /g, '')) return a.trim() ? 1 : 0
  const docs = allPlaces.map(placeKeys)
  const weight = (k: string) => (VENUE_WORDS.has(k) ? 0 : Math.log((docs.length + 1) / (1 + docs.filter((d) => d.has(k)).length)) + 0.1)
  const ka = placeKeys(a)
  const kb = placeKeys(b)
  let shared = 0
  let all = 0
  for (const k of new Set([...ka, ...kb])) {
    const w = weight(k)
    all += w
    if (ka.has(k) && kb.has(k)) shared += w
  }
  return all === 0 ? 0 : shared / all
}

export const samePlace = (a: string, b: string, allPlaces: readonly string[]): boolean => placeSimilarity(a, b, allPlaces) >= 0.5

/**
 * The same saved food: the same name (ignoring case, tashkeel and أ/ا، ة/ه، ى/ي) at the same restaurant, or both with
 * none. Names are compared by spelling, not sound: Coke and Cake sound alike but are different foods.
 */
export function sameFood(nameA: string, placeA: string, nameB: string, placeB: string, allPlaces: readonly string[]): boolean {
  if (words(nameA).join(' ') !== words(nameB).join(' ')) return false
  if (!placeA.trim() || !placeB.trim()) return !placeA.trim() && !placeB.trim()
  return samePlace(placeA, placeB, allPlaces)
}
