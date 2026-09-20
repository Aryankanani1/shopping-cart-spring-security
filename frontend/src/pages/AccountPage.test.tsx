import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../context/AuthContext'
import { AccountPage } from './AccountPage'
import { setSession } from '../api/tokenStore'
import type { UserDto } from '../api/types'

vi.mock('../api/users', () => ({
  usersApi: { get: vi.fn(), update: vi.fn(), remove: vi.fn() },
}))

import { usersApi } from '../api/users'

const USER: UserDto = { id: 7, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@b.com', cart: null }

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
})

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
})
