import { describe, it, expect, beforeEach, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { RegisterPage } from './RegisterPage'
import { ApiError } from '../api/client'
import { renderPage } from '../test/renderPage'

vi.mock('../context/AuthContext', () => ({ useAuth: vi.fn() }))
import { useAuth } from '../context/AuthContext'

const register = vi.fn()

beforeEach(() => {
  register.mockReset()
  vi.mocked(useAuth).mockReturnValue({ register } as unknown as ReturnType<typeof useAuth>)
})

function submit() {
  fireEvent.change(screen.getByLabelText('First name'), { target: { value: 'Ada' } })
  fireEvent.change(screen.getByLabelText('Last name'), { target: { value: 'Lovelace' } })
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'ada@example.com' } })
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'secret123' } })
  fireEvent.click(screen.getByRole('button', { name: 'Create account' }))
}

describe('RegisterPage', () => {
  it('creates the account and goes home', async () => {
    register.mockResolvedValue(undefined)
    renderPage(<RegisterPage />, { path: '/register' })

    submit()

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/))
    expect(register).toHaveBeenCalledWith({
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@example.com',
      password: 'secret123',
    })
  })

  it('shows validation errors next to their fields', async () => {
    register.mockRejectedValue(
      new ApiError(400, 'One or more fields are invalid', {
        fieldErrors: { password: 'Password must be 6-72 characters' },
      }),
    )
    renderPage(<RegisterPage />, { path: '/register' })

    submit()

    expect(await screen.findByText('Password must be 6-72 characters')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('shows other errors (a taken email) as one message', async () => {
    register.mockRejectedValue(new ApiError(409, 'ada@example.com already exists'))
    renderPage(<RegisterPage />, { path: '/register' })

    submit()

    expect(await screen.findByRole('alert')).toHaveTextContent('ada@example.com already exists')
    expect(screen.getByTestId('location')).toHaveTextContent('/register')
  })

  it('caps the password at BCrypt’s 72 characters', () => {
    renderPage(<RegisterPage />, { path: '/register' })

    expect(screen.getByLabelText('Password')).toHaveAttribute('maxLength', '72')
  })
})
