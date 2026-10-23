import type { ComponentChildren } from 'preact'

/** Only these schemes become links; anything else is shown as plain text. */
const SAFE = /^(https?:|mailto:|#)/i

function inline(s: string, k = 0): ComponentChildren[] {
  const out: ComponentChildren[] = []
  const re = /(\*\*|__)(.+?)\1|(\*|_)(.+?)\3|`([^`]+)`|\[([^\]]+)\]\(([^)\s]+)\)/g
  let last = 0
  let m: RegExpExecArray | null
  while ((m = re.exec(s))) {
    if (m.index > last) out.push(s.slice(last, m.index))
    const key = `${k}-${m.index}`
    if (m[2] !== undefined) out.push(<strong key={key}>{inline(m[2], m.index)}</strong>)
    else if (m[4] !== undefined) out.push(<em key={key}>{inline(m[4], m.index)}</em>)
    else if (m[5] !== undefined) out.push(<code key={key}>{m[5]}</code>)
    else if (SAFE.test(m[7])) out.push(<a key={key} href={m[7]} target="_blank" rel="noopener noreferrer">{m[6]}</a>)
    else out.push(m[6])
    last = m.index + m[0].length
  }
  if (last < s.length) out.push(s.slice(last))
  return out
}

/** Small safe renderer: headings, bold, italic, code, links, bullet and numbered lists, paragraphs. Builds elements, never HTML strings. */
export function Markdown({ text }: { text: string }) {
  const lines = text.replace(/\r\n?/g, '\n').split('\n')
  const blocks: ComponentChildren[] = []
  let para: string[] = []
  let list: { ordered: boolean; items: string[] } | null = null
  const flushPara = () => { if (para.length) { blocks.push(<p key={blocks.length}>{inline(para.join(' '))}</p>); para = [] } }
  const flushList = () => {
    if (!list) return
    const lis = list.items.map((x, i) => <li key={i}>{inline(x)}</li>)
    blocks.push(list.ordered ? <ol key={blocks.length}>{lis}</ol> : <ul key={blocks.length}>{lis}</ul>)
    list = null
  }
  for (const raw of lines) {
    const line = raw.trimEnd()
    const h = /^(#{1,6})\s+(.*)$/.exec(line)
    const li = /^\s*([-*+]|\d+[.)])\s+(.*)$/.exec(line)
    if (!line.trim()) { flushPara(); flushList() }
    else if (h) {
      flushPara(); flushList()
      blocks.push(<p key={blocks.length} class={`kit-md-h kit-md-h${Math.min(3, h[1].length)}`} role="heading" aria-level={Math.min(6, h[1].length + 2)}>{inline(h[2])}</p>)
    } else if (li) {
      flushPara()
      const ordered = /\d/.test(li[1])
      if (list && list.ordered !== ordered) flushList()
      if (!list) list = { ordered, items: [] }
      list.items.push(li[2])
    } else { flushList(); para.push(line.trim()) }
  }
  flushPara(); flushList()
  return <div class="kit-md" dir="auto">{blocks}</div>
}
