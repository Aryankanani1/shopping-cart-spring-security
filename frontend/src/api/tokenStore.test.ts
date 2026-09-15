import { describe, it, expect, beforeEach, vi } from 'vitest'
import { getSession, setSession, subscribe } from './tokenStore'

beforeEach(() => {
  localStorage.clear()
  setSession(null)
})

describe('tokenStore', () => {
  it('persists the session to localStorage and reads it back', () => {
    setSession({ id: 3, token: 'a', refreshToken: 'b' })

    expect(getSession()).toEqual({ id: 3, token: 'a', refreshToken: 'b' })
    expect(JSON.parse(localStorage.getItem('meridian.auth') ?? 'null')).toEqual({
      id: 3,
      token: 'a',
      refreshToken: 'b',
    })
  })

  it('clears the session on setSession(null)', () => {
    setSession({ id: 3, token: 'a', refreshToken: 'b' })
    setSession(null)

    expect(getSession()).toBeNull()
    expect(localStorage.getItem('meridian.auth')).toBeNull()
  })

  it('notifies subscribers on change and stops after unsubscribe', () => {
    const listener = vi.fn()
    const unsubscribe = subscribe(listener)

    setSession({ id: 1, token: 't', refreshToken: 'r' })
    expect(listener).toHaveBeenCalledWith({ id: 1, token: 't', refreshToken: 'r' })

    unsubscribe()
    setSession(null)
    expect(listener).toHaveBeenCalledTimes(1)
  })
})
