import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { authApi } from '../api/auth'
import type { RegisterBody } from '../api/auth'
import { getSession, setSession, subscribe } from '../api/tokenStore'
import type { AuthSession } from '../api/tokenStore'
import { isAdminToken } from '../lib/jwt'

interface AuthContextValue {
  session: AuthSession | null
  isAuthenticated: boolean
  userId: number | null
  /** Derived from the access token's `roles` claim — UI gating only; the server enforces authz. */
  isAdmin: boolean
  login: (email: string, password: string) => Promise<void>
  register: (body: RegisterBody) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSessionState] = useState<AuthSession | null>(() => getSession())

  // Stay in sync with the token store, including a forced logout when a token
  // refresh fails inside the API client.
  useEffect(() => subscribe(setSessionState), [])

  const value = useMemo<AuthContextValue>(() => {
    async function login(email: string, password: string) {
      const jwt = await authApi.login(email, password)
      setSession({ id: jwt.id, token: jwt.token, refreshToken: jwt.refreshToken })
    }
    async function register(body: RegisterBody) {
      await authApi.register(body)
      await login(body.email, body.password)
    }
    async function logout() {
      const current = getSession()
      try {
        if (current?.refreshToken) await authApi.logout(current.refreshToken)
      } catch {
        /* best effort — clear the client session regardless */
      }
      setSession(null)
    }
    return {
      session,
      isAuthenticated: session !== null,
      userId: session?.id ?? null,
      isAdmin: isAdminToken(session?.token),
      login,
      register,
      logout,
    }
  }, [session])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider')
  return ctx
}
