import { useEffect, useMemo, useState } from 'preact/hooks'

const COLORS = ['#ffc107', '#00bfa5', '#ff5252', '#448aff', '#e040fb']
const KEY = 'fq.egg.shownFor'

/** Easter egg: the first time a name with «رعد» or "r3d" is saved, a banner sweeps across with confetti. Tap to dismiss. */
export function RaadEgg({ name }: { name: string }) {
  const hit = name.includes('رعد') || name.toLowerCase().includes('r3d')
  const seen = () => { try { return localStorage.getItem(KEY) === name } catch { return false } }
  const [show, setShow] = useState(false)
  useEffect(() => {
    if (!hit || seen()) { setShow(false); return }
    try { localStorage.setItem(KEY, name) } catch { /* plays again next visit */ }
    try { navigator.vibrate?.([60, 80, 60]) } catch { /* no haptics */ }
    setShow(true)
    const id = setTimeout(() => setShow(false), 3300)
    return () => clearTimeout(id)
  }, [name, hit])
  const bits = useMemo(() => Array.from({ length: 40 }, () => ({ x: Math.random() * 100, y: Math.random() * 60, k: Math.floor(Math.random() * 5), s: 6 + Math.random() * 6 })), [])
  if (!show) return null
  return (
    <div class="home-egg" role="presentation" onClick={() => setShow(false)}>
      {bits.map((b, i) => (
        <span key={i} class="home-egg-bit" style={{ insetInlineStart: `${b.x}%`, insetBlockStart: `${b.y - 30}px`, inlineSize: `${b.s}px`, blockSize: `${b.s}px`, background: COLORS[b.k], animationDelay: `${(i % 8) * 0.05}s` }} />
      ))}
      <div class="home-egg-banner" dir="rtl">اسامه عمك!!!</div>
    </div>
  )
}
