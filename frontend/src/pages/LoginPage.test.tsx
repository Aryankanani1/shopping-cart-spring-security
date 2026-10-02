import { describe, it, expect, beforeEach, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { LoginPage } from './LoginPage'
import { ApiError } from '../api/client'
import { renderPage } from '../test/renderPage'

vi.mock('../context/AuthContext', () => ({ useAuth: vi.fn() }))
import { useAuth } from '../context/AuthContext'

const login = vi.fn()

beforeEach(() => {
  login.mockReset()
  vi.mocked(useAuth).mockReturnValue({ login } as unknown as ReturnType<typeof useAuth>)
})

function signIn(email: string, password: string) {
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: email } })
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: password } })
  fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))
}

describe('LoginPage', () => {
  it('signs in and returns to the page that sent the user here', async () => {
    login.mockResolvedValue(undefined)
    renderPage(<LoginPage />, { path: '/login', state: { from: '/checkout' } })

    signIn('ada@example.com', 'secret123')

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/checkout'))
    expect(login).toHaveBeenCalledWith('ada@example.com', 'secret123')
  })

  it('goes home when no page sent the user here', async () => {
    login.mockResolvedValue(undefined)
    renderPage(<LoginPage />, { path: '/login' })

    signIn('ada@example.com', 'secret123')

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/))
  })

  it('shows the server message on bad credentials and stays on the page', async () => {
    login.mockRejectedValue(new ApiError(401, 'Invalid email or password'))
    renderPage(<LoginPage />, { path: '/login' })

    signIn('ada@example.com', 'wrong')

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password')
    expect(screen.getByTestId('location')).toHaveTextContent('/login')
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled()
  })

  it('fills in the dev seed account', () => {
    renderPage(<LoginPage />, { path: '/login' })

    fireEvent.click(screen.getByRole('button', { name: /user1@gmail.com/ }))

    expect(screen.getByLabelText<HTMLInputElement>('Email').value).toBe('user1@gmail.com')
    expect(screen.getByLabelText<HTMLInputElement>('Password').value).toBe('123456')
  })
})
