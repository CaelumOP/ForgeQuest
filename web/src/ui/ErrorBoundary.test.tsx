import { render } from 'preact';
import { afterEach, describe, expect, it, vi } from 'vitest';

// The tests run in Node with no DOM library, so a tiny stand-in DOM is enough for Preact to draw into.
class FakeNode {
  childNodes: FakeNode[] = [];
  parentNode: FakeNode | null = null;
  attributes: Record<string, string> = {};
  style: Record<string, unknown> & { setProperty: (k: string, v: string) => void } = { setProperty() {} };
  data = '';
  constructor(public nodeType: number, public nodeName: string) {}
  get localName() { return this.nodeName.toLowerCase(); }
  get firstChild() { return this.childNodes[0] ?? null; }
  get nextSibling() {
    const sibs = this.parentNode?.childNodes ?? [];
    return sibs[sibs.indexOf(this) + 1] ?? null;
  }
  get textContent(): string { return this.nodeType === 3 ? this.data : this.childNodes.map((c) => c.textContent).join(''); }
  set textContent(v: string) { this.childNodes = []; if (v) this.appendChild(fakeDocument.createTextNode(v)); }
  insertBefore(n: FakeNode, ref: FakeNode | null) {
    n.parentNode?.removeChild(n);
    const i = ref ? this.childNodes.indexOf(ref) : -1;
    if (i < 0) this.childNodes.push(n); else this.childNodes.splice(i, 0, n);
    n.parentNode = this;
    return n;
  }
  appendChild(n: FakeNode) { return this.insertBefore(n, null); }
  removeChild(n: FakeNode) { this.childNodes = this.childNodes.filter((c) => c !== n); n.parentNode = null; return n; }
  remove() { this.parentNode?.removeChild(this); }
  setAttribute(k: string, v: string) { this.attributes[k] = String(v); }
  removeAttribute(k: string) { delete this.attributes[k]; }
  getAttribute(k: string) { return this.attributes[k] ?? null; }
  addEventListener() {}
  removeEventListener() {}
}
const fakeDocument = {
  createElement: (tag: string) => new FakeNode(1, tag.toUpperCase()),
  createElementNS: (_ns: string, tag: string) => new FakeNode(1, tag.toUpperCase()),
  createTextNode: (text: string) => Object.assign(new FakeNode(3, '#text'), { data: text }),
  documentElement: new FakeNode(1, 'HTML'),
};
vi.stubGlobal('document', fakeDocument);
vi.mock('./nav', () => ({ goBack: () => {} }));

const { ScreenBoundary } = await import('./ErrorBoundary');

function Broken(): never {
  throw new Error('bad record');
}

// Preact redraws a caught error on its next tick.
const tick = () => new Promise((r) => setTimeout(r, 0));

afterEach(() => vi.restoreAllMocks());

describe('ScreenBoundary', () => {
  it('shows the calm card with Back and Reload when a screen throws', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    const root = new FakeNode(1, 'DIV');
    render(<ScreenBoundary key="/a"><Broken /></ScreenBoundary>, root as unknown as Element);
    await tick();
    const text = root.textContent;
    expect(text).toContain('Something went wrong on this screen.');
    expect(text).toContain('Back');
    expect(text).toContain('Reload');
  });

  it('draws the screen as usual when nothing throws', () => {
    const root = new FakeNode(1, 'DIV');
    render(<ScreenBoundary key="/b"><p>fine</p></ScreenBoundary>, root as unknown as Element);
    expect(root.textContent).toBe('fine');
  });

  it('starts fresh when the route key changes', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    const root = new FakeNode(1, 'DIV');
    render(<ScreenBoundary key="/a"><Broken /></ScreenBoundary>, root as unknown as Element);
    await tick();
    expect(root.textContent).toContain('Reload');
    render(<ScreenBoundary key="/c"><p>next screen</p></ScreenBoundary>, root as unknown as Element);
    expect(root.textContent).toBe('next screen');
  });
});
