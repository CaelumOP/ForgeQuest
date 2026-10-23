import { useEffect, useState } from 'preact/hooks'
import { AiCheck } from './dev/AiCheck'

// Hash routes (#/progress) so GitHub Pages never 404s on a reload (decision 6).
function useRoute() {
  const [route, setRoute] = useState(location.hash.slice(1) || '/')
  useEffect(() => {
    const onChange = () => setRoute(location.hash.slice(1) || '/')
    addEventListener('hashchange', onChange)
    return () => removeEventListener('hashchange', onChange)
  }, [])
  return route
}

export function App() {
  const route = useRoute()
  if (route === '/check') return <AiCheck />
  return (
    <main class="page">
      <img src={`${import.meta.env.BASE_URL}icon.svg`} alt="" width={96} height={96} />
      <h1>ForgeQuest</h1>
      <p class="muted">Coming soon. / قريبًا.</p>
    </main>
  )
}
