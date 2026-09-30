import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../context/AuthContext'
import { CartProvider } from '../context/CartContext'
import { WishlistPage } from './WishlistPage'
import { setSession } from '../api/tokenStore'
import type { ProductDto, WishlistItemDto } from '../api/types'

vi.mock('../api/wishlist', () => ({
  wishlistApi: {
    list: vi.fn(),
    add: vi.fn(),
    remove: vi.fn(),
    setReminder: vi.fn(),
    clearReminder: vi.fn(),
    setAlerts: vi.fn(),
  },
}))
vi.mock('../api/users', () => ({
  usersApi: { get: vi.fn() },
}))

import { wishlistApi } from '../api/wishlist'
import { usersApi } from '../api/users'

function product(overrides: Partial<ProductDto> = {}): ProductDto {
  return {
    id: 5,
    name: 'Enamel Kettle',
    brand: 'Falcon',
    price: 48,
    description: '',
    inventory: 3,
    categoryName: 'Home & Kitchen',
    images: [],
    ...overrides,
  }
}

function item(overrides: Partial<WishlistItemDto> = {}): WishlistItemDto {
  return {
    product: product(),
    addedAt: '2026-09-01T10:00:00Z',
    priceWhenAdded: 48,
    remindAt: null,
    alertsEnabled: true,
    ...overrides,
  }
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <CartProvider>
          <MemoryRouter>
            <WishlistPage />
          </MemoryRouter>
        </CartProvider>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  localStorage.clear()
  setSession({ id: 7, token: 't', refreshToken: 'r' })
  vi.mocked(usersApi.get).mockReset().mockResolvedValue({
    id: 7,
    firstName: 'Ada',
    lastName: 'Lovelace',
    email: 'ada@b.com',
    cart: null,
  })
  Object.values(wishlistApi).forEach((fn) => vi.mocked(fn).mockReset())
})

describe('WishlistPage', () => {
  it('lists saved items, flagging price drops and sell-outs', async () => {
    vi.mocked(wishlistApi.list).mockResolvedValue([
      item({ product: product({ price: 40 }), priceWhenAdded: 48 }),
      item({ product: product({ id: 6, name: 'Desk Lamp', price: 30, inventory: 0 }), priceWhenAdded: 30 }),
    ])
    renderPage()

    expect(await screen.findByText('Enamel Kettle')).toBeInTheDocument()
    expect(screen.getByText('Price dropped')).toBeInTheDocument()
    expect(screen.getByText('$48.00')).toBeInTheDocument() // the struck-through old price
    expect(screen.getByText('Desk Lamp')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sold out' })).toBeDisabled()
  })

  it('shows an empty state when nothing is saved', async () => {
    vi.mocked(wishlistApi.list).mockResolvedValue([])
    renderPage()
    expect(await screen.findByText('Your wishlist is empty.')).toBeInTheDocument()
  })

  it('sends the chosen local time as an ISO instant and shows the reminder', async () => {
    const local = '2030-01-15T09:30'
    const iso = new Date(local).toISOString()
    vi.mocked(wishlistApi.list).mockResolvedValue([item()])
    vi.mocked(wishlistApi.setReminder).mockResolvedValue(item({ remindAt: iso }))
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: 'Set a reminder' }))
    fireEvent.change(screen.getByLabelText('Remind me on'), { target: { value: local } })
    fireEvent.click(screen.getByRole('button', { name: 'Save reminder' }))

    await waitFor(() => expect(wishlistApi.setReminder).toHaveBeenCalledWith(5, iso))
    expect(await screen.findByText(/Reminder: Jan 15, 2030/)).toBeInTheDocument()
  })

  it('refuses a time in the past without calling the API', async () => {
    vi.mocked(wishlistApi.list).mockResolvedValue([item()])
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: 'Set a reminder' }))
    const input = screen.getByLabelText('Remind me on')
    fireEvent.change(input, { target: { value: '2020-01-01T09:00' } })
    // Submit the form directly: this exercises our own check rather than the
    // browser's min= validation (which jsdom may or may not enforce).
    fireEvent.submit(input.closest('form') as HTMLFormElement)

    expect(await screen.findByText('Pick a time in the future.')).toBeInTheDocument()
    expect(wishlistApi.setReminder).not.toHaveBeenCalled()
  })

  it('turns alerts off for an item', async () => {
    vi.mocked(wishlistApi.list).mockResolvedValue([item()])
    vi.mocked(wishlistApi.setAlerts).mockResolvedValue(item({ alertsEnabled: false }))
    renderPage()

    const toggle = await screen.findByRole('checkbox', { name: /price drops and restocks/i })
    expect(toggle).toBeChecked()
    fireEvent.click(toggle)

    await waitFor(() => expect(wishlistApi.setAlerts).toHaveBeenCalledWith(5, false))
    await waitFor(() => expect(toggle).not.toBeChecked())
  })

  it('removes an item', async () => {
    vi.mocked(wishlistApi.list).mockResolvedValue([item()])
    vi.mocked(wishlistApi.remove).mockResolvedValue(null)
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: 'Remove' }))

    await waitFor(() => expect(wishlistApi.remove).toHaveBeenCalledWith(5))
    expect(await screen.findByText('Your wishlist is empty.')).toBeInTheDocument()
  })
})
