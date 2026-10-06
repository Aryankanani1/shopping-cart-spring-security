import { describe, it, expect, beforeEach, vi } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { AdminOrdersPage } from './AdminOrdersPage'
import { ApiError } from '../../api/client'
import { renderPage } from '../../test/renderPage'
import { orderSummary, paged } from '../../test/fixtures'
import { queryKeys } from '../../api/queryKeys'

// Keep the real NEXT_STATUSES map; only the network calls are mocked.
vi.mock('../../api/orders', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/orders')>()),
  ordersApi: { adminList: vi.fn(), updateStatus: vi.fn() },
}))
import { ordersApi } from '../../api/orders'

const PENDING = orderSummary({ id: 55, status: 'PENDING' })
const DELIVERED = orderSummary({ id: 56, status: 'DELIVERED', userEmail: 'grace@example.com' })

const rowFor = (orderId: number) => screen.getByText(`#${orderId}`).closest('tr') as HTMLElement

beforeEach(() => {
  vi.mocked(ordersApi.adminList).mockReset().mockResolvedValue(paged([PENDING, DELIVERED]))
  vi.mocked(ordersApi.updateStatus).mockReset()
})

describe('AdminOrdersPage', () => {
  it('lists every order with its customer, total and status', async () => {
    renderPage(<AdminOrdersPage />)

    await screen.findByText('#55')
    expect(within(rowFor(55)).getByText('ada@example.com')).toBeInTheDocument()
    expect(within(rowFor(55)).getByText('$48.00')).toBeInTheDocument()
    expect(within(rowFor(56)).getByText('delivered')).toBeInTheDocument()
  })

  it('lists the order of a deleted account without a customer', async () => {
    const orphaned = orderSummary({ id: 57, status: 'DELIVERED', userId: null, userEmail: null })
    vi.mocked(ordersApi.adminList).mockResolvedValue(paged([orphaned]))
    renderPage(<AdminOrdersPage />)

    await screen.findByText('#57')
    expect(within(rowFor(57)).getByText('Deleted account')).toBeInTheDocument()
  })

  it('offers only the legal next statuses', async () => {
    renderPage(<AdminOrdersPage />)
    await screen.findByText('#55')

    const options = within(within(rowFor(55)).getByRole('combobox')).getAllByRole('option')
    expect(options.map((o) => o.textContent)).toEqual(['Move to…', 'processing', 'cancelled'])
    // A delivered order is final.
    expect(within(rowFor(56)).queryByRole('combobox')).not.toBeInTheDocument()
  })

  it('moves an order on and reloads the list', async () => {
    vi.mocked(ordersApi.updateStatus).mockResolvedValue({} as never)
    renderPage(<AdminOrdersPage />)
    await screen.findByText('#55')

    fireEvent.change(within(rowFor(55)).getByRole('combobox'), { target: { value: 'PROCESSING' } })

    await waitFor(() => expect(ordersApi.updateStatus).toHaveBeenCalledWith(55, 'PROCESSING'))
    await waitFor(() => expect(ordersApi.adminList).toHaveBeenCalledTimes(2))
  })

  it('marks catalogue stock as stale when an order is cancelled', async () => {
    vi.mocked(ordersApi.updateStatus).mockResolvedValue({} as never)
    const { queryClient } = renderPage(<AdminOrdersPage />)
    await screen.findByText('#55')
    queryClient.setQueryData(queryKeys.products.detail(1), {})

    fireEvent.change(within(rowFor(55)).getByRole('combobox'), { target: { value: 'CANCELLED' } })

    await waitFor(() => expect(ordersApi.updateStatus).toHaveBeenCalledWith(55, 'CANCELLED'))
    await waitFor(() => expect(queryClient.getQueryState(queryKeys.products.detail(1))?.isInvalidated).toBe(true))
  })

  it('shows a refused status change', async () => {
    vi.mocked(ordersApi.updateStatus).mockRejectedValue(
      new ApiError(409, 'Cannot change order status from PENDING to SHIPPED'),
    )
    renderPage(<AdminOrdersPage />)
    await screen.findByText('#55')

    fireEvent.change(within(rowFor(55)).getByRole('combobox'), { target: { value: 'PROCESSING' } })

    expect(await screen.findByRole('alert')).toHaveTextContent('Cannot change order status')
  })

  it('pages through orders', async () => {
    vi.mocked(ordersApi.adminList).mockResolvedValue(paged([PENDING], 0, 2))
    renderPage(<AdminOrdersPage />)

    fireEvent.click(await screen.findByRole('button', { name: 'Next →' }))

    await waitFor(() => expect(ordersApi.adminList).toHaveBeenLastCalledWith(1))
  })

  it('says so when there are no orders', async () => {
    vi.mocked(ordersApi.adminList).mockResolvedValue(paged([]))
    renderPage(<AdminOrdersPage />)

    expect(await screen.findByText('No orders yet')).toBeInTheDocument()
  })
})
