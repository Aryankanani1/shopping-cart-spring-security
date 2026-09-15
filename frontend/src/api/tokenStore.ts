// The single source of truth for the signed-in session. Persisted to
// localStorage so a reload keeps the user logged in. The API client reads/writes
// it directly (to attach the bearer token and to rotate on refresh), and
// AuthContext subscribes so the UI reacts to a forced logout (failed refresh).

export interface AuthSession {
  id: number
  token: string
  refreshToken: string
}

const KEY = 'meridian.auth'

type Listener = (session: AuthSession | null) => void
const listeners = new Set<Listener>()

function load(): AuthSession | null {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? (JSON.parse(raw) as AuthSession) : null
  } catch {
    return null
  }
}

let session: AuthSession | null = load()

export function getSession(): AuthSession | null {
  return session
}

export function setSession(next: AuthSession | null): void {
  session = next
  try {
    if (next) localStorage.setItem(KEY, JSON.stringify(next))
    else localStorage.removeItem(KEY)
  } catch {
    /* storage unavailable — keep the in-memory copy */
  }
  listeners.forEach((l) => l(session))
}

export function subscribe(listener: Listener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
