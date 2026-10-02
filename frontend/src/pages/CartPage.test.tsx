import { describe, it, expect, beforeEach, vi } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { CartPage } from './CartPage'
import { ApiError } from '../api/client'
import { renderPage } from '../test/renderPage'
import { cartWith } from '../test/fixtures'
import type { CartDto } from '../api/types'

vi.mock('../context/CartContext', () => ({ useCart: vi.fn() }))
import { useCart } from '../context/CartContext'

const setQuantity = vi.fn()
const removeItem = vi.fn()
const clear = vi.fn()

function withCart(cart: CartDto | null, loading = false) {
  vi.mocked(useCart).mockReturnValue({
    cart,
    loading,
    itemCount: 0,
    refresh: vi.fn(),
    addItem: vi.fn(),
    setQuantity,
    removeItem,
    clear,
  })
}

beforeEach(() => {
  setQuantity.mockReset().mockResolvedValue(undefined)
  removeItem.mockReset().mockResolvedValue(undefined)
  clear.mockReset().mockResolvedValue(undefined)
})

describe('CartPage', () => {
  it('shows a loader while the cart loads', () => {
    withCart(null, true)
    renderPage(<CartPage />, { path: '/cart' })

    expect(screen.getByRole('status')).toHaveTextContent('Loading your bag')
  })

  it('shows an empty bag with a link to the shop', () => {
    withCart(cartWith([]))
    renderPage(<CartPage />, { path: '/cart' })

    expect(screen.getByText('Your bag is empty.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Start shopping' })).toHaveAttribute('href', '/products')
  })

  it('lists each line with its total, and the bag total', () => {
    withCart(cartWith([[10, 1, 2, 24], [11, 2, 1, 5.5]]))
    renderPage(<CartPage />, { path: '/cart' })

    const lines = screen.getAllByRole('listitem')
    expect(within(lines[0]).getByText('$48.00')).toBeInTheDocument()
    expect(within(lines[1]).getByText('$5.50')).toBeInTheDocument()
    expect(screen.getAllByText('$53.50')).toHaveLength(2) // subtotal and total
  })

  it('changes a quantity by the line’s item id', async () => {
    withCart(cartWith([[10, 1, 2, 24]]))
    renderPage(<CartPage />, { path: '/cart' })

    fireEvent.click(screen.getByRole('button', { name: 'Increase quantity' }))

    await waitFor(() => expect(setQuantity).toHaveBeenCalledWith(10, 3))
  })

  it('removes a line by its product id', async () => {
    withCart(cartWith([[10, 1, 2, 24]]))
    renderPage(<CartPage />, { path: '/cart' })

    fireEvent.click(screen.getByRole('button', { name: 'Remove' }))

    await waitFor(() => expect(removeItem).toHaveBeenCalledWith(1))
  })

  it('shows the server message when a change fails (not enough stock)', async () => {
    setQuantity.mockRejectedValue(new ApiError(409, 'Only 2 of Product 1 left in stock'))
    withCart(cartWith([[10, 1, 2, 24]]))
    renderPage(<CartPage />, { path: '/cart' })

    fireEvent.click(screen.getByRole('button', { name: 'Increase quantity' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Only 2 of Product 1 left in stock')
  })

  it('empties the bag', async () => {
    withCart(cartWith([[10, 1, 2, 24]]))
    renderPage(<CartPage />, { path: '/cart' })

    fireEvent.click(screen.getByRole('button', { name: 'Empty bag' }))

    await waitFor(() => expect(clear).toHaveBeenCalled())
  })

  it('goes to checkout', () => {
    withCart(cartWith([[10, 1, 2, 24]]))
    renderPage(<CartPage />, { path: '/cart' })

    fireEvent.click(screen.getByRole('button', { name: 'Checkout' }))

    expect(screen.getByTestId('location')).toHaveTextContent('/checkout')
  })
})
