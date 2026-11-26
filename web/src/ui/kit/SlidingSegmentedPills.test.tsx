import { render } from 'preact'
import { afterEach, describe, expect, it, vi } from 'vitest'

class FakeNode {
  childNodes: FakeNode[] = []
  parentNode: FakeNode | null = null
  attributes: Record<string, string> = {}
  style: Record<string, unknown> & { setProperty: (k: string, v: string) => void } = { setProperty() {} }
  data = ''
  constructor(public nodeType: number, public nodeName: string) {}
  get localName() { return this.nodeName.toLowerCase() }
  get firstChild() { return this.childNodes[0] ?? null }
  get nextSibling() {
    const sibs = this.parentNode?.childNodes ?? []
    return sibs[sibs.indexOf(this) + 1] ?? null
  }
  get textContent(): string { return this.nodeType === 3 ? this.data : this.childNodes.map((c) => c.textContent).join('') }
  set textContent(v: string) { this.childNodes = []; if (v) this.appendChild(fakeDocument.createTextNode(v)) }
  insertBefore(n: FakeNode, ref: FakeNode | null) {
    n.parentNode?.removeChild(n)
    const i = ref ? this.childNodes.indexOf(ref) : -1
    if (i < 0) this.childNodes.push(n); else this.childNodes.splice(i, 0, n)
    n.parentNode = this
    return n
  }
  appendChild(n: FakeNode) { return this.insertBefore(n, null) }
  removeChild(n: FakeNode) { this.childNodes = this.childNodes.filter((c) => c !== n); n.parentNode = null; return n }
  remove() { this.parentNode?.removeChild(this) }
  setAttribute(k: string, v: string) { this.attributes[k] = String(v) }
  removeAttribute(k: string) { delete this.attributes[k] }
  getAttribute(k: string) { return this.attributes[k] ?? null }
  addEventListener() {}
  removeEventListener() {}
}

const fakeDocument = {
  createElement: (tag: string) => new FakeNode(1, tag.toUpperCase()),
  createElementNS: (_ns: string, tag: string) => new FakeNode(1, tag.toUpperCase()),
  createTextNode: (text: string) => Object.assign(new FakeNode(3, '#text'), { data: text }),
  documentElement: new FakeNode(1, 'HTML'),
}

vi.stubGlobal('document', fakeDocument)

const {
  SlidingSegmentedPills,
  slidingPillOffsetFraction,
  slidingPillTranslatePercent,
} = await import('./SlidingSegmentedPills')

function findAll(root: FakeNode, pred: (n: FakeNode) => boolean): FakeNode[] {
  const out: FakeNode[] = []
  if (pred(root)) out.push(root)
  for (const c of root.childNodes) out.push(...findAll(c, pred))
  return out
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.stubGlobal('document', fakeDocument)
})

describe('SlidingSegmentedPills math (Android parity)', () => {
  it('tracks LTR offset and translateX percent across options', () => {
    expect(slidingPillOffsetFraction(0, 3, false)).toBeCloseTo(0, 4)
    expect(slidingPillOffsetFraction(1, 3, false)).toBeCloseTo(1 / 3, 4)
    expect(slidingPillOffsetFraction(2, 3, false)).toBeCloseTo(2 / 3, 4)
    expect(slidingPillTranslatePercent(0, 3, false)).toBe(0)
    expect(slidingPillTranslatePercent(1, 3, false)).toBe(100)
    expect(slidingPillTranslatePercent(2, 3, false)).toBe(200)
  })

  it('mirrors RTL offset and translateX percent', () => {
    expect(slidingPillOffsetFraction(0, 3, true)).toBeCloseTo(2 / 3, 4)
    expect(slidingPillOffsetFraction(1, 3, true)).toBeCloseTo(1 / 3, 4)
    expect(slidingPillOffsetFraction(2, 3, true)).toBeCloseTo(0, 4)
    expect(slidingPillTranslatePercent(0, 3, true)).toBe(-0)
    expect(slidingPillTranslatePercent(1, 3, true)).toBe(-100)
    expect(slidingPillTranslatePercent(2, 3, true)).toBe(-200)
  })

  it('handles out-of-bounds indices and zero count safely', () => {
    expect(slidingPillOffsetFraction(1, 0, false)).toBe(0)
    expect(slidingPillOffsetFraction(-1, 4, false)).toBe(0)
    expect(slidingPillOffsetFraction(10, 4, false)).toBeCloseTo(0.75, 4)
    expect(slidingPillOffsetFraction(10, 4, true)).toBeCloseTo(0, 4)
    expect(slidingPillTranslatePercent(1, 0, false)).toBe(0)
    expect(slidingPillTranslatePercent(-1, 4, false)).toBe(0)
    expect(slidingPillTranslatePercent(10, 4, false)).toBe(300)
  })
})

describe('SlidingSegmentedPills component', () => {
  const opts = [
    { value: '2', label: '2' },
    { value: '3', label: '3' },
    { value: '4', label: '4' },
  ]

  it('exposes radiogroup semantics, aria-checked state, and a single sliding pill indicator', () => {
    const root = new FakeNode(1, 'DIV')
    render(
      <SlidingSegmentedPills label="Days" options={opts} value="3" onChange={() => {}} />,
      root as unknown as Element,
    )
    const groups = findAll(root, (n) => n.attributes.role === 'radiogroup')
    expect(groups).toHaveLength(1)
    expect(groups[0]!.attributes['aria-label']).toBe('Days')

    const radios = findAll(root, (n) => n.attributes.role === 'radio')
    expect(radios).toHaveLength(3)
    expect(radios.map((r) => r.attributes['aria-checked'])).toEqual(['false', 'true', 'false'])

    const pills = findAll(root, (n) => (n.attributes.class ?? '').includes('kit-slide-pill'))
    expect(pills).toHaveLength(1)
    expect(String(pills[0]!.style.inlineSize)).toBe('calc(100% / 3)')
    expect(String(pills[0]!.style.transform)).toContain('translateX(calc(1 * 100% * var(--seg-dir, 1)))')
  })

  it('hides the indicator when value is null and snaps when reduced motion is active', () => {
    const root = new FakeNode(1, 'DIV')
    render(
      <SlidingSegmentedPills label="Where" options={opts} value={null} onChange={() => {}} />,
      root as unknown as Element,
    )
    expect(findAll(root, (n) => (n.attributes.class ?? '').includes('kit-slide-pill'))).toHaveLength(0)

    vi.stubGlobal('matchMedia', () => ({ matches: true }))
    render(
      <SlidingSegmentedPills label="Where" options={opts} value="4" onChange={() => {}} />,
      root as unknown as Element,
    )
    const pills = findAll(root, (n) => (n.attributes.class ?? '').includes('kit-slide-pill'))
    expect(pills).toHaveLength(1)
    expect(pills[0]!.attributes.class).toContain('kit-slide-pill-snap')
  })
})
