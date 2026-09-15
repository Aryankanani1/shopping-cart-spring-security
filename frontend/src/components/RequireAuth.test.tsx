import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../context/AuthContext'
import { RequireAuth } from './RequireAuth'
import { setSession } from '../api/tokenStore'

function renderAt(path: string) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/login" element={<div>Login page</div>} />
          <Route
            path="/secret"
            element={
              <RequireAuth>
                <div>Secret content</div>
              </RequireAuth>
            }
          />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

beforeEach(() => {
  localStorage.clear()
  setSession(null)
})

describe('RequireAuth', () => {
  it('redirects to /login when unauthenticated', () => {
    renderAt('/secret')
    expect(screen.getByText('Login page')).toBeInTheDocument()
    expect(screen.queryByText('Secret content')).toBeNull()
  })

  it('renders the protected content when authenticated', () => {
    setSession({ id: 1, token: 't', refreshToken: 'r' })
    renderAt('/secret')
    expect(screen.getByText('Secret content')).toBeInTheDocument()
  })
})
