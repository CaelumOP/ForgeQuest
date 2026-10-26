import { describe, expect, it } from 'vitest'
// The same cases as app/src/test/java/.../domain/FoodSearchTest.kt, so both apps find the same foods.
import casesJson from '../../../app/src/test/resources/food_search_cases.json?raw'
import { normalize, placeSimilarity, sameFood, samePlace, score, soundKey, words } from './food-search'

type Cases = {
  foods: { name: string; place: string }[]
  search: { q: string; has: string[]; not?: string[] }[]
  sound: [string, string][]
  places: string[]
  samePlace: { a: string; b: string; same: boolean }[]
  sameFood: { a: [string, string]; b: [string, string]; same: boolean }[]
}
const cases = JSON.parse(casesJson) as Cases
const found = (q: string) => cases.foods.filter((f) => score(q, f.name, f.place) > 0).map((f) => f.name)

describe('food search (shared cases)', () => {
  for (const c of cases.search) {
    it(`"${c.q}" finds ${c.has.join(', ') || 'nothing'}`, () => {
      const hits = found(c.q)
      for (const name of c.has) expect(hits, c.q).toContain(name)
      for (const name of c.not ?? []) expect(hits, c.q).not.toContain(name)
      if (!c.has.length && !c.not) expect(hits).toEqual([])
    })
  }
  for (const [a, b] of cases.sound) {
    it(`${a} sounds like ${b}`, () => expect(soundKey(words(a)[0])).toBe(soundKey(words(b)[0])))
  }
  for (const c of cases.samePlace) {
    it(`${c.a} ${c.same ? '=' : '≠'} ${c.b}`, () => expect(samePlace(c.a, c.b, cases.places), String(placeSimilarity(c.a, c.b, cases.places))).toBe(c.same))
  }
  for (const c of cases.sameFood) {
    it(`same food: ${c.a.join(' @ ')} / ${c.b.join(' @ ')}`, () => expect(sameFood(c.a[0], c.a[1], c.b[0], c.b[1], cases.places)).toBe(c.same))
  }
})

describe('food search details', () => {
  it('normalizes Arabic letter forms, tashkeel, digits and punctuation', () => {
    expect(normalize('أَفْراحٌ إآ ة ى ـ ٣٤')).toBe('افراح اا ه ي 34')
    expect(normalize("Barn's  Coffee!")).toBe('barns coffee')
  })
  it('an exact name ranks above a sound-alike', () => {
    expect(score('shawarma', 'Chicken shawarma')).toBeGreaterThan(score('shawarma', 'شاورما لحم'))
  })
})
