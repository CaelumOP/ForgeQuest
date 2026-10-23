import { useEffect, useState } from 'preact/hooks'

// Hash routes (#/progress) so GitHub Pages never 404s on a reload (decision 7).
// Each history entry the app makes is stamped with its depth, so Back knows whether an
// in-app page sits behind the current one (opened from a link: no, so Back goes home).

type Stamp = { fqDepth: number }
const depthOf = (state: unknown) => (state as Stamp | null)?.fqDepth

let depth = depthOf(history.state) ?? 0
if (depthOf(history.state) === undefined) history.replaceState({ ...(history.state ?? {}), fqDepth: 0 }, '')

addEventListener('hashchange', () => {
  const seen = depthOf(history.state)
  if (seen === undefined) {
    depth += 1
    history.replaceState({ fqDepth: depth }, '')
  } else {
    depth = seen
  }
})

export const currentRoute = () => location.hash.slice(1) || '/'

export function useRoute(): string {
  const [route, setRoute] = useState(currentRoute)
  useEffect(() => {
    const onChange = () => setRoute(currentRoute())
    addEventListener('hashchange', onChange)
    return () => removeEventListener('hashchange', onChange)
  }, [])
  return route
}

export function goBack() {
  if (depth > 0) history.back()
  else location.replace('#/')
}
