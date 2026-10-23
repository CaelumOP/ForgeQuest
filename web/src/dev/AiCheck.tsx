import { useState } from 'preact/hooks'

// Plan step 3: proves Gemini and Groq answer calls made straight from Safari. Keys stay in memory only.
async function check(url: string, headers: Record<string, string>): Promise<string> {
  try {
    const r = await fetch(url, { headers })
    return r.ok ? 'OK: the browser can call it' : `Reached it, but HTTP ${r.status} (check the key)`
  } catch {
    return 'Blocked: the browser cannot call it'
  }
}

export function AiCheck() {
  const [gemini, setGemini] = useState('')
  const [groq, setGroq] = useState('')
  const [geminiResult, setGeminiResult] = useState('')
  const [groqResult, setGroqResult] = useState('')

  const run = async () => {
    if (gemini) {
      setGeminiResult('Checking…')
      setGeminiResult(await check('https://generativelanguage.googleapis.com/v1beta/models?pageSize=1', { 'x-goog-api-key': gemini }))
    }
    if (groq) {
      setGroqResult('Checking…')
      setGroqResult(await check('https://api.groq.com/openai/v1/models', { Authorization: `Bearer ${groq}` }))
    }
  }

  return (
    <main class="page">
      <h1>AI check</h1>
      <label>Gemini key<input type="password" value={gemini} onInput={(e) => setGemini(e.currentTarget.value)} /></label>
      <p class="muted">{geminiResult}</p>
      <label>Groq key<input type="password" value={groq} onInput={(e) => setGroq(e.currentTarget.value)} /></label>
      <p class="muted">{groqResult}</p>
      <button onClick={run}>Check</button>
    </main>
  )
}
