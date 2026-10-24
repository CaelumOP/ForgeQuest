import { useEffect, useState } from 'preact/hooks'

// #/viewport: measures how tall the iPhone Home Screen app really is, to fix the strip under the tab bar.
// Four bars mark where each kind of "full height" ends; the numbers say how tall each one is.
const probe = (css: string): number => {
  const el = document.createElement('div')
  el.style.cssText = `position:absolute;visibility:hidden;inline-size:1px;${css}`
  document.body.appendChild(el)
  const h = el.getBoundingClientRect().height
  el.remove()
  return Math.round(h)
}

function read() {
  const inset = (side: string) => probe(`block-size:env(safe-area-inset-${side})`)
  return {
    'screen.height': screen.height,
    'innerHeight': innerHeight,
    'visualViewport': Math.round(visualViewport?.height ?? 0),
    'html clientHeight': document.documentElement.clientHeight,
    '100vh': probe('block-size:100vh'),
    '100dvh': probe('block-size:100dvh'),
    '100svh': probe('block-size:100svh'),
    '100lvh': probe('block-size:100lvh'),
    'safe top': inset('top'),
    'safe bottom': inset('bottom'),
    'standalone': String(matchMedia('(display-mode: standalone)').matches),
  }
}

const bar = (color: string, css: string) => <div style={`position:fixed;inset-inline:0;block-size:6px;background:${color};${css}`} />

export function ViewportCheck() {
  const [m, setM] = useState(read)
  useEffect(() => {
    const again = () => setM(read())
    addEventListener('resize', again)
    const id = setTimeout(again, 500)
    return () => { removeEventListener('resize', again); clearTimeout(id) }
  }, [])
  return (
    <div style="padding:calc(env(safe-area-inset-top) + 16px) 16px 16px;font:14px/1.6 system-ui;color:var(--text)">
      <h1 style="font-size:18px;margin:0 0 8px">Viewport check</h1>
      <p style="margin:0 0 8px;color:var(--text-2)">Take one screenshot of this whole screen and send it.</p>
      <table>{Object.entries(m).map(([k, v]) => <tr key={k}><td style="padding-inline-end:16px">{k}</td><td><b>{v}</b></td></tr>)}</table>
      <p style="margin:12px 0 0;color:var(--text-2)">Bottom bars: red = fixed bottom 0, yellow = 100dvh, green = 100lvh, blue = 100vh.</p>
      {bar('#ff3b30', 'bottom:0')}
      {bar('#ffcc00', 'top:calc(100dvh - 6px)')}
      {bar('#34c759', 'top:calc(100lvh - 6px)')}
      {bar('#0a84ff', 'top:calc(100vh - 12px)')}
    </div>
  )
}
