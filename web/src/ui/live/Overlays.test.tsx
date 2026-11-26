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
  getBoundingClientRect() { return { width: 320, height: 12, top: 0, left: 0, bottom: 12, right: 320 } }
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

const { RestBar } = await import('./Overlays')

function findAll(root: FakeNode, pred: (n: FakeNode) => boolean): FakeNode[] {
  const out: FakeNode[] = []
  if (pred(root)) out.push(root)
  for (const c of root.childNodes) out.push(...findAll(c, pred))
  return out
}

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.stubGlobal('document', fakeDocument)
})

describe('RestBar WaveBar', () => {
  it('drives progress from endsAt via requestAnimationFrame', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(10_000)

    const rafCallbacks = new Map<number, FrameRequestCallback>()
    let nextId = 1
    const raf = vi.fn((cb: FrameRequestCallback) => {
      const id = nextId++
      rafCallbacks.set(id, cb)
      return id
    })
    const caf = vi.fn((id: number) => {
      rafCallbacks.delete(id)
    })
    vi.stubGlobal('requestAnimationFrame', raf)
    vi.stubGlobal('cancelAnimationFrame', caf)
    vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener() {}, removeEventListener() {} }))

    const root = new FakeNode(1, 'DIV')
    const props = {
      rest: { totalSeconds: 60, endsAt: 70_000, nextExercise: 'Squat' },
      now: 10_000,
      nextLabel: 'Squat · 8 reps',
      canCopyLastSet: false,
      showPresets: false,
      onTogglePresets: () => {},
      onAdd: () => {},
      onRemove: () => {},
      onSelectPreset: () => {},
      onCopyLastSet: () => {},
      onSkip: () => {},
    }

    render(<RestBar {...props} />, root as unknown as Element)
    // Preact runs effects after a 100 ms fallback timer when rAF was absent at import.
    await vi.advanceTimersByTimeAsync(100)

    expect(raf).toHaveBeenCalled()
    expect(findAll(root, (n) => n.attributes.class === 'lw-wave-track')).toHaveLength(0)
    expect(findAll(root, (n) => n.attributes.class === 'lw-wave-stop')).toHaveLength(0)
    expect(findAll(root, (n) => (n.attributes.class ?? '').includes('lw-wave'))).not.toHaveLength(0)

    const scheduledId = raf.mock.results[0]?.value as number
    vi.setSystemTime(40_000)
    const firstCb = rafCallbacks.get(scheduledId)
    firstCb?.(16)
    await vi.advanceTimersByTimeAsync(0)
    expect(raf).toHaveBeenCalledTimes(2)
    const track = findAll(root, (n) => n.attributes.class === 'lw-wave-track')[0]
    const flat = findAll(root, (n) => n.attributes.class === 'lw-wave-flat')[0]
    expect(Number(track.attributes.x1) - Number(flat.attributes.x2) - 4).toBe(6)
    expect(track.attributes.x2).toBe('318')
    const stop = findAll(root, (n) => n.attributes.class === 'lw-wave-stop')[0]
    expect(stop.attributes.cx).toBe('318')
    expect(stop.attributes.r).toBe('2')
    const wave = findAll(root, (n) => n.attributes.class === 'lw-wave')[0]
    expect(wave.attributes.d).toMatch(/^M2\.00 6\.00 /)
    expect(wave.attributes.d).toMatch(/L160\.00 6\.00$/)

    vi.setSystemTime(70_000)
    rafCallbacks.get(raf.mock.results[1].value)?.(32)
    await vi.advanceTimersByTimeAsync(0)
    expect(findAll(root, (n) => n.attributes.class === 'lw-wave')).toHaveLength(0)
    expect(findAll(root, (n) => n.attributes.class === 'lw-wave-track')[0].attributes.x1).toBe('2')
    expect(raf).toHaveBeenCalledTimes(2)

    // Unmount cleanup (cancelAnimationFrame) is not exercised by this hand-rolled fake DOM;
    // it is checked in the browser smoke test instead.
    void caf
  })

  it('renders static flat progress line without wave path under prefers-reduced-motion', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(10_000)
    vi.stubGlobal('requestAnimationFrame', vi.fn(() => 1))
    vi.stubGlobal('cancelAnimationFrame', vi.fn())
    vi.stubGlobal('matchMedia', () => ({ matches: true, addEventListener() {}, removeEventListener() {} }))

    const root = new FakeNode(1, 'DIV')
    render(
      <RestBar
        rest={{ totalSeconds: 60, endsAt: 70_000, nextExercise: null }}
        now={10_000}
        nextLabel={null}
        canCopyLastSet={false}
        showPresets={false}
        onTogglePresets={() => {}}
        onAdd={() => {}}
        onRemove={() => {}}
        onSelectPreset={() => {}}
        onCopyLastSet={() => {}}
        onSkip={() => {}}
      />,
      root as unknown as Element,
    )
    await vi.advanceTimersByTimeAsync(100)

    expect(findAll(root, (n) => n.attributes.class === 'lw-wave')).toHaveLength(0)
    const flat = findAll(root, (n) => (n.attributes.class ?? '').includes('lw-wave-flat-on'))
    expect(flat).toHaveLength(1)
  })
})
