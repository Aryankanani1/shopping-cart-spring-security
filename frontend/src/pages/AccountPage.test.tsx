import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../context/AuthContext'
import { AccountPage } from './AccountPage'
import { getSession, setSession } from '../api/tokenStore'
import { ApiError } from '../api/client'
import type { UserDto } from '../api/types'

vi.mock('../api/users', () => ({
  usersApi: { get: vi.fn(), update: vi.fn(), remove: vi.fn() },
}))

vi.mock('../api/auth', () => ({
  authApi: { login: vi.fn(), register: vi.fn(), logout: vi.fn(), changePassword: vi.fn() },
}))

import { usersApi } from '../api/users'
import { authApi } from '../api/auth'

const USER: UserDto = { id: 7, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@b.com' }

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter>
          <AccountPage />
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  localStorage.clear()
  setSession({ id: 7, token: 't', refreshToken: 'r' })
  vi.mocked(usersApi.get).mockReset().mockResolvedValue(USER)
  vi.mocked(usersApi.update).mockReset().mockResolvedValue(USER)
  vi.mocked(authApi.changePassword).mockReset()
})

async function fillPasswordForm(current: string, next: string, confirm = next) {
  fireEvent.change(await screen.findByLabelText('Current password'), { target: { value: current } })
  fireEvent.change(screen.getByLabelText('New password'), { target: { value: next } })
  fireEvent.change(screen.getByLabelText('Confirm new password'), { target: { value: confirm } })
  fireEvent.click(screen.getByRole('button', { name: /change password/i }))
}

describe('AccountPage', () => {
  it('loads the profile into the form and shows the read-only email', async () => {
    renderPage()
    await waitFor(() =>
      expect(screen.getByLabelText<HTMLInputElement>('First name').value).toBe('Ada'),
    )
    expect(screen.getByLabelText<HTMLInputElement>('Last name').value).toBe('Lovelace')
    const email = screen.getByLabelText<HTMLInputElement>('Email')
    expect(email.value).toBe('ada@b.com')
    expect(email).toBeDisabled()
  })

  it('saves edited name via the API', async () => {
    renderPage()
    const first = await screen.findByLabelText<HTMLInputElement>('First name')

    // Save is disabled until something actually changes.
    const save = screen.getByRole('button', { name: /save changes/i })
    expect(save).toBeDisabled()

    fireEvent.change(first, { target: { value: 'Grace' } })
    expect(save).toBeEnabled()
    fireEvent.click(save)

    await waitFor(() =>
      expect(usersApi.update).toHaveBeenCalledWith(7, { firstName: 'Grace', lastName: 'Lovelace' }),
    )
  })

  it('changes the password and adopts the fresh session the server returns', async () => {
    vi.mocked(authApi.changePassword).mockResolvedValue({ id: 7, token: 't2', refreshToken: 'r2' })
    renderPage()
    await fillPasswordForm('old-secret', 'new-secret')

    expect(await screen.findByText(/password changed/i)).toBeInTheDocument()
    expect(authApi.changePassword).toHaveBeenCalledWith('old-secret', 'new-secret')
    expect(getSession()).toEqual({ id: 7, token: 't2', refreshToken: 'r2' })
    expect(screen.getByLabelText<HTMLInputElement>('Current password').value).toBe('')
  })

  it('does not call the API when the confirmation does not match', async () => {
    renderPage()
    await fillPasswordForm('old-secret', 'new-secret', 'new-secreT')

    expect(await screen.findByText('Passwords don’t match')).toBeInTheDocument()
    expect(authApi.changePassword).not.toHaveBeenCalled()
  })

  it('shows a wrong current password next to that field', async () => {
    vi.mocked(authApi.changePassword).mockRejectedValue(
      new ApiError(400, 'Current password is incorrect', {
        fieldErrors: { currentPassword: 'Current password is incorrect' },
      }),
    )
    renderPage()
    await fillPasswordForm('wrong-one', 'new-secret')

    expect(await screen.findByText('Current password is incorrect')).toBeInTheDocument()
    // The existing session is untouched when the change is rejected.
    expect(getSession()).toEqual({ id: 7, token: 't', refreshToken: 'r' })
  })
})
