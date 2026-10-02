import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { AdminProductsPage } from './AdminProductsPage'
import { ApiError } from '../../api/client'
import { renderPage } from '../../test/renderPage'
import { paged, product } from '../../test/fixtures'

vi.mock('../../api/products', () => ({
  productsApi: { list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn() },
}))
vi.mock('../../api/categories', () => ({ categoriesApi: { list: vi.fn() } }))
vi.mock('../../api/images', () => ({ imagesApi: { upload: vi.fn(), remove: vi.fn() } }))
import { productsApi } from '../../api/products'
import { categoriesApi } from '../../api/categories'

const LAMP = product({ id: 1, name: 'Desk Lamp', price: 24, inventory: 10, categoryName: 'Lighting' })

const rowFor = (name: string) => screen.getByRole('cell', { name }).closest('tr') as HTMLElement

beforeEach(() => {
  vi.mocked(productsApi.list).mockReset().mockResolvedValue(paged([LAMP]))
  vi.mocked(productsApi.get).mockReset().mockResolvedValue(LAMP)
  vi.mocked(productsApi.create).mockReset()
  vi.mocked(productsApi.update).mockReset()
  vi.mocked(productsApi.remove).mockReset().mockResolvedValue(null)
  vi.mocked(categoriesApi.list).mockReset().mockResolvedValue([
    { id: 1, name: 'Lighting' },
    { id: 2, name: 'Books' },
  ])
})

afterEach(() => {
  vi.restoreAllMocks()
})

function fill(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

describe('AdminProductsPage', () => {
  it('lists products with price and stock', async () => {
    renderPage(<AdminProductsPage />)

    await screen.findByRole('cell', { name: 'Desk Lamp' })
    expect(within(rowFor('Desk Lamp')).getByText('$24.00')).toBeInTheDocument()
    expect(within(rowFor('Desk Lamp')).getByText('10')).toBeInTheDocument()
    expect(productsApi.list).toHaveBeenCalledWith({ page: 0, size: 20, sort: 'id' })
  })

  it('creates a product, then keeps it open for images', async () => {
    vi.mocked(productsApi.create).mockResolvedValue(product({ id: 9, name: 'Novel', categoryName: 'Books' }))
    renderPage(<AdminProductsPage />)
    await screen.findByRole('cell', { name: 'Desk Lamp' })

    fireEvent.click(screen.getByRole('button', { name: '+ New product' }))
    fill('Name', ' Novel ')
    fill('Brand', 'Penguin')
    fill('Price', '12.5')
    fill('Inventory', '4')
    fill('Category', 'Books')
    fireEvent.click(screen.getByRole('button', { name: 'Create product' }))

    await waitFor(() =>
      expect(productsApi.create).toHaveBeenCalledWith({
        name: 'Novel',
        brand: 'Penguin',
        price: 12.5,
        inventory: 4,
        description: '',
        categoryName: 'Books',
      }),
    )
    // Now in edit mode for the new product, so images can be added.
    expect(await screen.findByRole('heading', { name: 'Edit Novel' })).toBeInTheDocument()
  })

  it('edits an existing product', async () => {
    vi.mocked(productsApi.update).mockResolvedValue(LAMP)
    renderPage(<AdminProductsPage />)
    await screen.findByRole('cell', { name: 'Desk Lamp' })

    fireEvent.click(within(rowFor('Desk Lamp')).getByRole('button', { name: 'Edit' }))
    fill('Price', '30')
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() =>
      expect(productsApi.update).toHaveBeenCalledWith(1, expect.objectContaining({ price: 30, name: 'Desk Lamp' })),
    )
    await waitFor(() => expect(screen.queryByRole('heading', { name: 'Edit Desk Lamp' })).not.toBeInTheDocument())
  })

  it('closes the edit form on cancel', async () => {
    renderPage(<AdminProductsPage />)
    await screen.findByRole('cell', { name: 'Desk Lamp' })

    fireEvent.click(within(rowFor('Desk Lamp')).getByRole('button', { name: 'Edit' }))
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(screen.queryByRole('heading', { name: 'Edit Desk Lamp' })).not.toBeInTheDocument()
    expect(productsApi.update).not.toHaveBeenCalled()
  })

  it('deletes only after confirming', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderPage(<AdminProductsPage />)
    await screen.findByRole('cell', { name: 'Desk Lamp' })

    fireEvent.click(within(rowFor('Desk Lamp')).getByRole('button', { name: 'Delete' }))

    await waitFor(() => expect(productsApi.remove).toHaveBeenCalledWith(1))
  })

  it('explains why an ordered product can’t be deleted', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(productsApi.remove).mockRejectedValue(
      new ApiError(409, "This product is part of existing orders, so it can't be deleted."),
    )
    renderPage(<AdminProductsPage />)
    await screen.findByRole('cell', { name: 'Desk Lamp' })

    fireEvent.click(within(rowFor('Desk Lamp')).getByRole('button', { name: 'Delete' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('part of existing orders')
  })
})
