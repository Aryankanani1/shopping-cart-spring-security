import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from './AuthContext'
import { CartProvider, useCart } from './CartContext'
import { setSession } from '../api/tokenStore'
import type { CartDto } from '../api/types'
import { ApiError } from '../api/client'

// The cart comes from GET /carts/mine; add-to-cart and the line changes hit cartApi too.
vi.mock('../api/cart', () => ({
  cartApi: { mine: vi.fn(), addItem: vi.fn(), updateQuantity: vi.fn(), removeItem: vi.fn(), clear: vi.fn() },
}))

import { cartApi } from '../api/cart'

function cartWith(items: Array<{ itemId: number; quantity: number }>): CartDto {
  return {
    cartId: 500,
    totalAmount: items.reduce((n, i) => n + i.quantity * 10, 0),
    cartItems: items.map((i) => ({
      itemId: i.itemId,
      quantity: i.quantity,
      unitPrice: 10,
      product: {
        id: i.itemId,
        name: `P${i.itemId}`,
        brand: 'B',
        price: 10,
        description: '',
        inventory: 5,
        categoryName: 'C',
        images: [],
      },
    })),
  }
}

function Probe() {
  const { itemCount, cart, addItem } = useCart()
  return (
    <div>
      <span data-testid="count">{itemCount}</span>
      <span data-testid="cartId">{cart?.cartId ?? 'none'}</span>
      <button onClick={() => void addItem(1, 2)}>add</button>
    </div>
  )
}

function renderCart() {
  // Fresh client per render so cache never leaks between tests; no retries so a
  // rejected query/mutation surfaces immediately.
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <CartProvider>
          <Probe />
        </CartProvider>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  localStorage.clear()
  setSession(null)
  vi.mocked(cartApi.mine).mockReset()
  vi.mocked(cartApi.addItem).mockReset()
})

describe('CartContext', () => {
  it('is empty and makes no API call when signed out', () => {
    renderCart()
    expect(cartApi.mine).not.toHaveBeenCalled()
    expect(screen.getByTestId('count').textContent).toBe('0')
  })

  it('loads the caller\'s cart and sums item quantities', async () => {
    setSession({ id: 7, token: 't', refreshToken: 'r' })
    vi.mocked(cartApi.mine).mockResolvedValue(cartWith([{ itemId: 1, quantity: 2 }, { itemId: 2, quantity: 3 }]))

    renderCart()

    await waitFor(() => expect(screen.getByTestId('count').textContent).toBe('5'))
    expect(screen.getByTestId('cartId').textContent).toBe('500')
  })

  it('treats a 404 as no cart yet', async () => {
    setSession({ id: 7, token: 't', refreshToken: 'r' })
    vi.mocked(cartApi.mine).mockRejectedValue(new ApiError(404, 'You have no cart yet'))

    renderCart()

    await waitFor(() => expect(cartApi.mine).toHaveBeenCalled())
    await waitFor(() => expect(screen.getByTestId('cartId').textContent).toBe('none'))
    expect(screen.getByTestId('count').textContent).toBe('0')
  })

  it('addItem calls the API then refreshes the cart', async () => {
    setSession({ id: 7, token: 't', refreshToken: 'r' })
    vi.mocked(cartApi.mine).mockResolvedValue(cartWith([{ itemId: 1, quantity: 1 }]))
    vi.mocked(cartApi.addItem).mockResolvedValue(null)

    renderCart()
    await waitFor(() => expect(screen.getByTestId('count').textContent).toBe('1'))

    // Simulate the server-side cart growing after the add.
    vi.mocked(cartApi.mine).mockResolvedValue(cartWith([{ itemId: 1, quantity: 4 }]))
    fireEvent.click(screen.getByText('add'))

    await waitFor(() => expect(screen.getByTestId('count').textContent).toBe('4'))
    expect(cartApi.addItem).toHaveBeenCalledWith(1, 2)
  })
})
