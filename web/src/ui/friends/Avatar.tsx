import { useEffect, useState } from 'preact/hooks'
import type { Person } from '../../domain/forge-score'
import './friends.css'

export function initialsFor(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[1][0]).toUpperCase()
}

/** Photo when shared (and it loads), else initials. Decorative: callers label the row. */
export function Avatar({ person, small }: { person: Person; small?: boolean }) {
  const [imgFailed, setImgFailed] = useState(false)
  useEffect(() => { setImgFailed(false) }, [person.photoUrl])
  return (
    <span class={small ? 'fr-avatar sm' : 'fr-avatar'} aria-hidden="true">
      {person.photoUrl && !imgFailed
        ? <img src={person.photoUrl} alt="" referrerPolicy="no-referrer" onError={() => setImgFailed(true)} />
        : initialsFor(person.name)}
    </span>
  )
}
