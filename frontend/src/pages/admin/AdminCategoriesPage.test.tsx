import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { AdminCategoriesPage } from './AdminCategoriesPage'
import { ApiError } from '../../api/client'
import { renderPage } from '../../test/renderPage'

vi.mock('../../api/categories', () => ({
  categoriesApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn() },
}))
import { categoriesApi } from '../../api/categories'

const BOOKS = { id: 1, name: 'Books' }

const rowFor = (name: string) => screen.getByText(name).closest('li') as HTMLElement

beforeEach(() => {
  vi.mocked(categoriesApi.list).mockReset().mockResolvedValue([BOOKS])
  vi.mocked(categoriesApi.create).mockReset().mockResolvedValue({ id: 2, name: 'Garden' })
  vi.mocked(categoriesApi.update).mockReset().mockResolvedValue({ id: 1, name: 'Novels' })
  vi.mocked(categoriesApi.remove).mockReset().mockResolvedValue(null)
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('AdminCategoriesPage', () => {
  it('adds a trimmed category, clears the field and reloads', async () => {
    renderPage(<AdminCategoriesPage />)
    await screen.findByText('Books')

    const input = screen.getByPlaceholderText('New category name')
    fireEvent.change(input, { target: { value: '  Garden ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add' }))

    await waitFor(() => expect(categoriesApi.create).toHaveBeenCalledWith('Garden'))
    await waitFor(() => expect(input).toHaveValue(''))
    expect(categoriesApi.list).toHaveBeenCalledTimes(2)
  })

  it('won’t add a blank name', async () => {
    renderPage(<AdminCategoriesPage />)
    await screen.findByText('Books')

    fireEvent.change(screen.getByPlaceholderText('New category name'), { target: { value: '   ' } })

    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled()
  })

  it('shows a duplicate-name error', async () => {
    vi.mocked(categoriesApi.create).mockRejectedValue(new ApiError(409, 'Category already exists'))
    renderPage(<AdminCategoriesPage />)
    await screen.findByText('Books')

    fireEvent.change(screen.getByPlaceholderText('New category name'), { target: { value: 'Books' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Category already exists')
  })

  it('renames a category', async () => {
    renderPage(<AdminCategoriesPage />)
    await screen.findByText('Books')

    fireEvent.click(within(rowFor('Books')).getByRole('button', { name: 'Rename' }))
    fireEvent.change(screen.getByDisplayValue('Books'), { target: { value: 'Novels' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(categoriesApi.update).toHaveBeenCalledWith(1, 'Novels'))
  })

  it('deletes only after confirming', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    renderPage(<AdminCategoriesPage />)
    await screen.findByText('Books')

    fireEvent.click(within(rowFor('Books')).getByRole('button', { name: 'Delete' }))
    expect(categoriesApi.remove).not.toHaveBeenCalled()

    fireEvent.click(within(rowFor('Books')).getByRole('button', { name: 'Delete' }))
    await waitFor(() => expect(categoriesApi.remove).toHaveBeenCalledWith(1))
    expect(confirm).toHaveBeenCalledTimes(2)
  })
})
