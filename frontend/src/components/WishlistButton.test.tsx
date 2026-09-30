import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../context/AuthContext'
import { WishlistButton } from './WishlistButton'
import { setSession } from '../api/tokenStore'
import type { WishlistItemDto } from '../api/types'

vi.mock('../api/wishlist', () => ({
  wishlistApi: { list: vi.fn(), add: vi.fn(), remove: vi.fn() },
}))

import { wishlistApi } from '../api/wishlist'

const SAVED: WishlistItemDto = {
  product: {
    id: 5,
    name: 'Enamel Kettle',
    brand: 'Falcon',
    price: 48,
    description: '',
    inventory: 3,
    categoryName: 'Home & Kitchen',
    images: [],
  },
  addedAt: '2026-09-01T10:00:00Z',
  priceWhenAdded: 48,
  remindAt: null,
  alertsEnabled: true,
}

function renderButton() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter initialEntries={['/products/5']}>
          <Routes>
            <Route path="/products/:id" element={<WishlistButton productId={5} />} />
            <Route path="/login" element={<p>Login page</p>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  localStorage.clear()
  setSession(null)
  Object.values(wishlistApi).forEach((fn) => vi.mocked(fn).mockReset())
})

describe('WishlistButton', () => {
  it('sends a signed-out visitor to log in', async () => {
    renderButton()
    fireEvent.click(screen.getByRole('button', { name: /save to wishlist/i }))

    expect(await screen.findByText('Login page')).toBeInTheDocument()
    expect(wishlistApi.list).not.toHaveBeenCalled()
  })

  it('saves and unsaves for a signed-in user', async () => {
    setSession({ id: 7, token: 't', refreshToken: 'r' })
    vi.mocked(wishlistApi.list).mockResolvedValue([])
    vi.mocked(wishlistApi.add).mockResolvedValue(SAVED)
    vi.mocked(wishlistApi.remove).mockResolvedValue(null)
    renderButton()

    const button = screen.getByRole('button', { name: /save to wishlist/i })
    await waitFor(() => expect(wishlistApi.list).toHaveBeenCalled())
    expect(button).toHaveAttribute('aria-pressed', 'false')

    fireEvent.click(button)
    await waitFor(() => expect(button).toHaveAttribute('aria-pressed', 'true'))
    expect(wishlistApi.add).toHaveBeenCalledWith(5)
    expect(button).toHaveTextContent('Saved to wishlist')

    fireEvent.click(button)
    await waitFor(() => expect(button).toHaveAttribute('aria-pressed', 'false'))
    expect(wishlistApi.remove).toHaveBeenCalledWith(5)
  })
})
