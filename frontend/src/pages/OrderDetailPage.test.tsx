import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { OrderDetailPage } from './OrderDetailPage'
import { ApiError } from '../api/client'
import { renderPage } from '../test/renderPage'
import { order } from '../test/fixtures'
import { queryKeys } from '../api/queryKeys'

vi.mock('../context/AuthContext', () => ({ useAuth: vi.fn() }))
vi.mock('../api/orders', () => ({ ordersApi: { get: vi.fn(), cancel: vi.fn() } }))
import { useAuth } from '../context/AuthContext'
import { ordersApi } from '../api/orders'

function open(state?: unknown) {
  return renderPage(<OrderDetailPage />, { path: '/orders/:id', url: '/orders/55', state })
}

beforeEach(() => {
  vi.mocked(useAuth).mockReturnValue({ userId: 7 } as unknown as ReturnType<typeof useAuth>)
  vi.mocked(ordersApi.get).mockReset().mockResolvedValue(order())
  vi.mocked(ordersApi.cancel).mockReset()
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('OrderDetailPage', () => {
  it('shows the lines, total and shipping address', async () => {
    open()

    expect(await screen.findByRole('heading', { name: '#55' })).toBeInTheDocument()
    expect(screen.getByText('Desk Lamp')).toBeInTheDocument()
    expect(screen.getAllByText('$48.00').length).toBeGreaterThan(0)
    expect(screen.getByText('1 Analytical Way')).toBeInTheDocument()
    expect(screen.getByText('London, LDN, EC1A')).toBeInTheDocument()
    expect(ordersApi.get).toHaveBeenCalledWith(55)
  })

  it('thanks the customer right after checkout', async () => {
    open({ justPlaced: true })

    expect(await screen.findByText(/your order has been placed/)).toBeInTheDocument()
  })

  it('cancels after confirming, and shows the new status', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    // Like the server: once cancelled, fetching the order returns it cancelled too.
    vi.mocked(ordersApi.cancel).mockImplementation(async () => {
      vi.mocked(ordersApi.get).mockResolvedValue(order({ status: 'CANCELLED' }))
      return order({ status: 'CANCELLED' })
    })
    open()

    fireEvent.click(await screen.findByRole('button', { name: 'Cancel order' }))

    await waitFor(() => expect(ordersApi.cancel).toHaveBeenCalledWith(55))
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument())
    expect(screen.getByText(/cancelled/i)).toBeInTheDocument()
  })

  it('marks catalogue stock and the wishlist as stale after a cancel', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(ordersApi.cancel).mockResolvedValue(order({ status: 'CANCELLED' }))
    const { queryClient } = open()
    const cached = [queryKeys.products.detail(1), queryKeys.wishlist(7)]
    cached.forEach((key) => queryClient.setQueryData(key, {}))

    fireEvent.click(await screen.findByRole('button', { name: 'Cancel order' }))

    await waitFor(() => expect(ordersApi.cancel).toHaveBeenCalledWith(55))
    for (const key of cached) {
      await waitFor(() => expect(queryClient.getQueryState(key)?.isInvalidated, key.join('/')).toBe(true))
    }
  })

  it('does nothing if the customer backs out', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    open()

    fireEvent.click(await screen.findByRole('button', { name: 'Cancel order' }))

    expect(ordersApi.cancel).not.toHaveBeenCalled()
  })

  it('offers no cancel once shipped', async () => {
    vi.mocked(ordersApi.get).mockResolvedValue(order({ status: 'SHIPPED' }))
    open()

    await screen.findByRole('heading', { name: '#55' })
    expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument()
  })

  it('shows a refused cancel', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(ordersApi.cancel).mockRejectedValue(
      new ApiError(409, 'Cannot change order status from SHIPPED to CANCELLED'),
    )
    open()

    fireEvent.click(await screen.findByRole('button', { name: 'Cancel order' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Cannot change order status')
  })

  it('shows someone else’s order as an error, not the order', async () => {
    vi.mocked(ordersApi.get).mockRejectedValue(
      new ApiError(403, 'You do not have permission to perform this action'),
    )
    open()

    expect(await screen.findByRole('alert')).toHaveTextContent('You do not have permission')
    expect(screen.queryByRole('heading', { name: '#55' })).not.toBeInTheDocument()
  })
})
