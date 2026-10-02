// The single source of truth for the signed-in session. Persisted to
// localStorage so a reload keeps the user logged in. The API client reads/writes
// it directly (to attach the bearer token and to rotate on refresh), and
// AuthContext subscribes so the UI reacts to a forced logout (failed refresh).
//
// Every tab of the app shares the stored session, so changes made in one tab
// (a refreshed token pair, a sign-in, a sign-out) are picked up by the others.
// Without that, a tab would keep presenting a refresh token another tab has
// already spent, and the server treats a spent token as stolen.

export interface AuthSession {
  id: number
  token: string
  refreshToken: string
}

const KEY = 'meridian.auth'

type Listener = (session: AuthSession | null) => void
const listeners = new Set<Listener>()

/** The stored session. Throws if storage is unavailable. */
function readStorage(): AuthSession | null {
  const raw = localStorage.getItem(KEY)
  return raw ? (JSON.parse(raw) as AuthSession) : null
}

function load(): AuthSession | null {
  try {
    return readStorage()
  } catch {
    return null
  }
}

let session: AuthSession | null = load()

function sameSession(a: AuthSession | null, b: AuthSession | null): boolean {
  return a?.id === b?.id && a?.token === b?.token && a?.refreshToken === b?.refreshToken
}

function notify(): void {
  listeners.forEach((l) => l(session))
}

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
  notify()
}

/**
 * Adopt whatever session is stored now, which another tab may have changed, and
 * return it. Subscribers are notified only if it differs from this tab's copy.
 */
export function syncFromStorage(): AuthSession | null {
  let stored: AuthSession | null
  try {
    stored = readStorage()
  } catch {
    return session // storage unavailable — the in-memory copy is all there is
  }
  if (!sameSession(stored, session)) {
    session = stored
    notify()
  }
  return session
}

// Fired in every other tab when one tab writes the session (key null = storage cleared).
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event) => {
    if (event.key === KEY || event.key === null) syncFromStorage()
  })
}

export function subscribe(listener: Listener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
