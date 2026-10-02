import { describe, it, expect, beforeEach, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { ProductsPage } from './ProductsPage'
import { ApiError } from '../api/client'
import { renderPage } from '../test/renderPage'
import { paged, product } from '../test/fixtures'

vi.mock('../api/products', () => ({ productsApi: { list: vi.fn() } }))
vi.mock('../api/categories', () => ({ categoriesApi: { list: vi.fn() } }))
import { productsApi } from '../api/products'
import { categoriesApi } from '../api/categories'

beforeEach(() => {
  vi.mocked(productsApi.list).mockReset().mockResolvedValue(paged([product()]))
  vi.mocked(categoriesApi.list).mockReset().mockResolvedValue([
    { id: 1, name: 'Books' },
    { id: 2, name: 'Lighting' },
  ])
})

const lastQuery = () => vi.mocked(productsApi.list).mock.lastCall?.[0]

describe('ProductsPage', () => {
  it('lists the newest products first, 12 a page', async () => {
    renderPage(<ProductsPage />, { path: '/products' })

    expect(await screen.findByText('Desk Lamp')).toBeInTheDocument()
    expect(lastQuery()).toEqual({ name: '', category: '', sort: 'id,desc', page: 0, size: 12 })
  })

  it('searches by name through the URL', async () => {
    renderPage(<ProductsPage />, { path: '/products' })

    fireEvent.change(screen.getByLabelText('Search products'), { target: { value: '  lamp ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Search' }))

    await waitFor(() => expect(lastQuery()).toMatchObject({ name: 'lamp', page: 0 }))
    expect(screen.getByTestId('location')).toHaveTextContent('/products?name=lamp')
  })

  it('filters by category and goes back to the first page', async () => {
    renderPage(<ProductsPage />, { path: '/products', url: '/products?page=2' })
    await screen.findByRole('option', { name: 'Books' })

    fireEvent.change(screen.getByLabelText('Filter by category'), { target: { value: 'Books' } })

    await waitFor(() => expect(lastQuery()).toMatchObject({ category: 'Books', page: 0 }))
  })

  it('sorts with an allowlisted field', async () => {
    renderPage(<ProductsPage />, { path: '/products' })

    fireEvent.change(screen.getByLabelText('Sort products'), { target: { value: 'price,asc' } })

    await waitFor(() => expect(lastQuery()).toMatchObject({ sort: 'price,asc' }))
  })

  it('pages forward', async () => {
    vi.mocked(productsApi.list).mockResolvedValue(paged([product()], 0, 3))
    renderPage(<ProductsPage />, { path: '/products' })

    fireEvent.click(await screen.findByRole('button', { name: 'Next →' }))

    await waitFor(() => expect(lastQuery()).toMatchObject({ page: 1 }))
  })

  it('offers to reset filters when nothing matches', async () => {
    vi.mocked(productsApi.list).mockResolvedValue(paged([]))
    renderPage(<ProductsPage />, { path: '/products', url: '/products?name=zzz' })

    fireEvent.click(await screen.findByRole('button', { name: 'Reset filters' }))

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/products$/))
  })

  it('shows a load failure', async () => {
    vi.mocked(productsApi.list).mockRejectedValue(new ApiError(500, 'An unexpected error occurred'))
    renderPage(<ProductsPage />, { path: '/products' })

    expect(await screen.findByRole('alert')).toHaveTextContent('An unexpected error occurred')
  })
})
