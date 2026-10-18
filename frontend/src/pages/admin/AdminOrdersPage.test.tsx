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
// The form has its own tests (RefundForm.test.tsx); here, only where it opens.
vi.mock('./RefundForm', () => ({
  RefundForm: ({ orderId }: { orderId: number }) => <p>Refund form for order {orderId}</p>,
}))

const PENDING = orderSummary({ id: 55, status: 'PENDING' })
const DELIVERED = orderSummary({ id: 56, status: 'DELIVERED', userEmail: 'grace@example.com' })
const PAID = orderSummary({ id: 58, status: 'PAID' })

const rowFor = (orderId: number) => screen.getByText(`#${orderId}`).closest('tr') as HTMLElement

beforeEach(() => {
  vi.mocked(ordersApi.adminList).mockReset().mockResolvedValue(paged([PENDING, DELIVERED, PAID]))
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

    const optionsFor = (orderId: number) =>
      within(within(rowFor(orderId)).getByRole('combobox')).getAllByRole('option').map((o) => o.textContent)
    // Unpaid: it can only be cancelled; paying is the customer's step, not the admin's.
    expect(optionsFor(55)).toEqual(['Move to…', 'cancelled'])
    // Paid: fulfil it, or cancel it (which refunds it).
    expect(optionsFor(58)).toEqual(['Move to…', 'processing', 'cancelled'])
    // A delivered order is final.
    expect(within(rowFor(56)).queryByRole('combobox')).not.toBeInTheDocument()
  })

  it('moves an order on and reloads the list', async () => {
    vi.mocked(ordersApi.updateStatus).mockResolvedValue({} as never)
    renderPage(<AdminOrdersPage />)
    await screen.findByText('#55')

    fireEvent.change(within(rowFor(58)).getByRole('combobox'), { target: { value: 'PROCESSING' } })

    await waitFor(() => expect(ordersApi.updateStatus).toHaveBeenCalledWith(58, 'PROCESSING'))
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
      new ApiError(409, 'Cannot change order status from PAID to PROCESSING'),
    )
    renderPage(<AdminOrdersPage />)
    await screen.findByText('#58')

    fireEvent.change(within(rowFor(58)).getByRole('combobox'), { target: { value: 'PROCESSING' } })

    expect(await screen.findByRole('alert')).toHaveTextContent('Cannot change order status')
  })

  it('offers a refund of a paid order until it is cancelled', async () => {
    const cancelled = orderSummary({ id: 59, status: 'CANCELLED' })
    vi.mocked(ordersApi.adminList).mockResolvedValue(paged([PENDING, DELIVERED, PAID, cancelled]))
    renderPage(<AdminOrdersPage />)
    await screen.findByText('#55')

    // Delivered orders too: that is where returns happen.
    expect(within(rowFor(56)).getByRole('button', { name: 'Refund' })).toBeInTheDocument()
    expect(within(rowFor(58)).getByRole('button', { name: 'Refund' })).toBeInTheDocument()
    // Unpaid, nothing to refund; cancelled, the whole payment is refunded already.
    expect(within(rowFor(55)).queryByRole('button', { name: 'Refund' })).not.toBeInTheDocument()
    expect(within(rowFor(59)).queryByRole('button', { name: 'Refund' })).not.toBeInTheDocument()
  })

  it('opens the refund form under an order, one at a time', async () => {
    renderPage(<AdminOrdersPage />)
    await screen.findByText('#55')

    fireEvent.click(within(rowFor(58)).getByRole('button', { name: 'Refund' }))
    expect(screen.getByText('Refund form for order 58')).toBeInTheDocument()
    expect(within(rowFor(58)).getByRole('button', { name: 'Close' })).toHaveAttribute('aria-expanded', 'true')

    fireEvent.click(within(rowFor(56)).getByRole('button', { name: 'Refund' }))
    expect(screen.getByText('Refund form for order 56')).toBeInTheDocument()
    expect(screen.queryByText('Refund form for order 58')).not.toBeInTheDocument()

    fireEvent.click(within(rowFor(56)).getByRole('button', { name: 'Close' }))
    expect(screen.queryByText(/^Refund form for/)).not.toBeInTheDocument()
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
