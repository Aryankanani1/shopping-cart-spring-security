import { describe, it, expect, beforeEach, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { ProductDetailPage } from './ProductDetailPage'
import { ApiError } from '../api/client'
import { renderPage } from '../test/renderPage'
import { product } from '../test/fixtures'

vi.mock('../context/AuthContext', () => ({ useAuth: vi.fn() }))
vi.mock('../context/CartContext', () => ({ useCart: vi.fn() }))
vi.mock('../api/products', () => ({ productsApi: { get: vi.fn() } }))
vi.mock('../api/wishlist', () => ({ wishlistApi: { list: vi.fn() } }))
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { productsApi } from '../api/products'
import { wishlistApi } from '../api/wishlist'

const addItem = vi.fn()

function signedIn(isAuthenticated: boolean) {
  vi.mocked(useAuth).mockReturnValue({
    isAuthenticated,
    userId: isAuthenticated ? 7 : null,
  } as unknown as ReturnType<typeof useAuth>)
}

function open(id = 1) {
  return renderPage(<ProductDetailPage />, { path: '/products/:id', url: `/products/${id}` })
}

beforeEach(() => {
  addItem.mockReset().mockResolvedValue(undefined)
  vi.mocked(useCart).mockReturnValue({ addItem } as unknown as ReturnType<typeof useCart>)
  vi.mocked(wishlistApi.list).mockReset().mockResolvedValue([])
  vi.mocked(productsApi.get).mockReset().mockResolvedValue(product({ id: 1, price: 24, inventory: 10 }))
  signedIn(true)
})

describe('ProductDetailPage', () => {
  it('shows the product, its price and stock', async () => {
    open()

    expect(await screen.findByRole('heading', { name: 'Desk Lamp' })).toBeInTheDocument()
    expect(screen.getByText('$24.00')).toBeInTheDocument()
    expect(screen.getByText('In stock')).toBeInTheDocument()
    expect(productsApi.get).toHaveBeenCalledWith(1)
  })

  it('adds the chosen quantity to the bag', async () => {
    open()
    await screen.findByRole('heading', { name: 'Desk Lamp' })

    fireEvent.click(screen.getByRole('button', { name: 'Increase quantity' }))
    fireEvent.click(screen.getByRole('button', { name: 'Add to bag' }))

    await waitFor(() => expect(addItem).toHaveBeenCalledWith(1, 2))
    expect(await screen.findByText(/Added to your bag/)).toBeInTheDocument()
  })

  it('sends a signed-out visitor to sign in, then back here', async () => {
    signedIn(false)
    open()

    fireEvent.click(await screen.findByRole('button', { name: 'Add to bag' }))

    expect(screen.getByTestId('location')).toHaveTextContent('/login')
    expect(addItem).not.toHaveBeenCalled()
  })

  it('shows why adding failed', async () => {
    addItem.mockRejectedValue(new ApiError(409, 'Only 10 of Desk Lamp left in stock'))
    open()

    fireEvent.click(await screen.findByRole('button', { name: 'Add to bag' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Only 10 of Desk Lamp left in stock')
  })

  it('warns when stock is low', async () => {
    vi.mocked(productsApi.get).mockResolvedValue(product({ inventory: 3 }))
    open()

    expect(await screen.findByText('Only 3 left')).toBeInTheDocument()
  })

  it('can’t be added once sold out', async () => {
    vi.mocked(productsApi.get).mockResolvedValue(product({ inventory: 0 }))
    open()

    const button = await screen.findByRole('button', { name: 'Sold out' })
    expect(button).toBeDisabled()
  })

  it('shows a missing product with a way back', async () => {
    vi.mocked(productsApi.get).mockRejectedValue(new ApiError(404, 'product not found'))
    open(999)

    expect(await screen.findByRole('alert')).toHaveTextContent('product not found')
    expect(screen.getByRole('link', { name: '← Back to shop' })).toHaveAttribute('href', '/products')
  })
})
