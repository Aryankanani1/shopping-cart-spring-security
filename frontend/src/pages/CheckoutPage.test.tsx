import { useState } from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { CheckoutPage } from './CheckoutPage'
import { ApiError } from '../api/client'
import { renderPage } from '../test/renderPage'
import { cartWith, order } from '../test/fixtures'
import { queryKeys } from '../api/queryKeys'
import type { CartDto } from '../api/types'

vi.mock('../context/AuthContext', () => ({ useAuth: vi.fn() }))
vi.mock('../context/CartContext', () => ({ useCart: vi.fn() }))
vi.mock('../api/orders', () => ({ ordersApi: { place: vi.fn() } }))
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { ordersApi } from '../api/orders'

const refresh = vi.fn()

function withCart(cart: CartDto | null) {
  vi.mocked(useCart).mockReturnValue({
    cart,
    loading: false,
    itemCount: 0,
    refresh,
    addItem: vi.fn(),
    setQuantity: vi.fn(),
    removeItem: vi.fn(),
    clear: vi.fn(),
  })
}

function fillAddress() {
  const fields: Record<string, string> = {
    'Recipient name': 'Ada Lovelace',
    'Address line 1': '1 Analytical Way',
    City: 'London',
    'State / region': 'LDN',
    'Postal code': 'EC1A',
    Country: 'UK',
  }
  for (const [label, value] of Object.entries(fields)) {
    fireEvent.change(screen.getByLabelText(label), { target: { value } })
  }
}

beforeEach(() => {
  refresh.mockReset().mockResolvedValue(undefined)
  vi.mocked(ordersApi.place).mockReset()
  vi.mocked(useAuth).mockReturnValue({ userId: 7 } as unknown as ReturnType<typeof useAuth>)
})

describe('CheckoutPage', () => {
  it('sends an empty bag back to the cart', () => {
    withCart(cartWith([]))
    renderPage(<CheckoutPage />, { path: '/checkout' })

    expect(screen.getByTestId('location')).toHaveTextContent('/cart')
  })

  it('summarises the bag', () => {
    withCart(cartWith([[10, 1, 2, 24]]))
    renderPage(<CheckoutPage />, { path: '/checkout' })

    expect(screen.getByText('Product 1')).toBeInTheDocument()
    expect(screen.getAllByText('$48.00')).toHaveLength(2) // line and total
  })

  it('places the order with the address and opens it', async () => {
    vi.mocked(ordersApi.place).mockResolvedValue(order({ id: 55 }))
    withCart(cartWith([[10, 1, 2, 24]]))
    renderPage(<CheckoutPage />, { path: '/checkout' })

    fillAddress()
    fireEvent.click(screen.getByRole('button', { name: 'Place order' }))

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/orders/55'))
    expect(ordersApi.place).toHaveBeenCalledWith({
      recipientName: 'Ada Lovelace',
      addressLine1: '1 Analytical Way',
      addressLine2: '',
      city: 'London',
      state: 'LDN',
      postalCode: 'EC1A',
      country: 'UK',
    })
    expect(refresh).toHaveBeenCalled() // the server emptied the cart
  })

  it('stays on checkout while the placed order empties the bag, then opens the order', async () => {
    // Placing the order removes the cart on the server, so the refresh after it
    // re-renders this page with no items before the page navigates to the order.
    // In the browser the router applies that navigation as a transition, after
    // the re-render: the page used to send the "empty" bag to /cart, replacing
    // the order page.
    let setCart: (cart: CartDto | null) => void = () => {}
    vi.mocked(useCart).mockImplementation(() => {
      const [cart, set] = useState<CartDto | null>(() => cartWith([[10, 1, 2, 24]]))
      setCart = set
      return { cart, loading: false, itemCount: 0, refresh, addItem: vi.fn(), setQuantity: vi.fn(), removeItem: vi.fn(), clear: vi.fn() }
    })
    let finishRefresh = () => {}
    refresh.mockImplementation(() => {
      setCart(null) // GET /carts/mine now answers 404
      return new Promise<void>((resolve) => (finishRefresh = resolve))
    })
    vi.mocked(ordersApi.place).mockResolvedValue(order({ id: 55 }))
    renderPage(<CheckoutPage />, { path: '/checkout' })

    fillAddress()
    fireEvent.click(screen.getByRole('button', { name: 'Place order' }))

    await waitFor(() => expect(refresh).toHaveBeenCalled())
    expect(screen.getByTestId('location')).toHaveTextContent('/checkout')
    expect(screen.getByText('Placing your order…')).toBeInTheDocument()

    finishRefresh()
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/orders/55'))
  })

  it('marks the order history, catalogue stock and wishlist as stale', async () => {
    vi.mocked(ordersApi.place).mockResolvedValue(order({ id: 55 }))
    withCart(cartWith([[10, 1, 2, 24]]))
    const { queryClient } = renderPage(<CheckoutPage />, { path: '/checkout' })
    const cached = [queryKeys.orders.history(7), queryKeys.products.detail(1), queryKeys.wishlist(7)]
    cached.forEach((key) => queryClient.setQueryData(key, {}))

    fillAddress()
    fireEvent.click(screen.getByRole('button', { name: 'Place order' }))

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/orders/55'))
    for (const key of cached) {
      expect(queryClient.getQueryState(key)?.isInvalidated, key.join('/')).toBe(true)
    }
  })

  it('shows why an order failed and stays on checkout', async () => {
    vi.mocked(ordersApi.place).mockRejectedValue(new ApiError(409, 'Only 1 of Product 1 left in stock'))
    withCart(cartWith([[10, 1, 2, 24]]))
    renderPage(<CheckoutPage />, { path: '/checkout' })

    fillAddress()
    fireEvent.click(screen.getByRole('button', { name: 'Place order' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Only 1 of Product 1 left in stock')
    expect(screen.getByTestId('location')).toHaveTextContent('/checkout')
    expect(refresh).not.toHaveBeenCalled()
  })
})
