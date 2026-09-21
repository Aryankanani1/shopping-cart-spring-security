import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from './AuthContext'
import { CartProvider, useCart } from './CartContext'
import { setSession } from '../api/tokenStore'
import type { CartDto, UserDto } from '../api/types'

// The cart is resolved from the user record; add-to-cart hits cartApi. Mock both.
vi.mock('../api/users', () => ({ usersApi: { get: vi.fn() } }))
vi.mock('../api/cart', () => ({
  cartApi: { addItem: vi.fn(), updateQuantity: vi.fn(), removeItem: vi.fn(), clear: vi.fn() },
}))

import { usersApi } from '../api/users'
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

function userWith(cart: CartDto | null): UserDto {
  return { id: 7, firstName: 'A', lastName: 'B', email: 'a@b.com', cart }
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
  vi.mocked(usersApi.get).mockReset()
  vi.mocked(cartApi.addItem).mockReset()
})

describe('CartContext', () => {
  it('is empty and makes no API call when signed out', () => {
    renderCart()
    expect(usersApi.get).not.toHaveBeenCalled()
    expect(screen.getByTestId('count').textContent).toBe('0')
  })

  it('resolves the cart from the user record and sums item quantities', async () => {
    setSession({ id: 7, token: 't', refreshToken: 'r' })
    vi.mocked(usersApi.get).mockResolvedValue(
      userWith(cartWith([{ itemId: 1, quantity: 2 }, { itemId: 2, quantity: 3 }])),
    )

    renderCart()

    await waitFor(() => expect(screen.getByTestId('count').textContent).toBe('5'))
    expect(usersApi.get).toHaveBeenCalledWith(7)
    expect(screen.getByTestId('cartId').textContent).toBe('500')
  })

  it('addItem calls the API then refreshes the cart', async () => {
    setSession({ id: 7, token: 't', refreshToken: 'r' })
    vi.mocked(usersApi.get).mockResolvedValue(userWith(cartWith([{ itemId: 1, quantity: 1 }])))
    vi.mocked(cartApi.addItem).mockResolvedValue(null)

    renderCart()
    await waitFor(() => expect(screen.getByTestId('count').textContent).toBe('1'))

    // Simulate the server-side cart growing after the add.
    vi.mocked(usersApi.get).mockResolvedValue(userWith(cartWith([{ itemId: 1, quantity: 4 }])))
    fireEvent.click(screen.getByText('add'))

    await waitFor(() => expect(screen.getByTestId('count').textContent).toBe('4'))
    expect(cartApi.addItem).toHaveBeenCalledWith(1, 2)
  })
})
