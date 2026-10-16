import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { OrderDetailPage } from './OrderDetailPage'
import { ApiError } from '../api/client'
import { renderPage } from '../test/renderPage'
import { order, payment } from '../test/fixtures'
import { queryKeys } from '../api/queryKeys'

vi.mock('../context/AuthContext', () => ({ useAuth: vi.fn() }))
vi.mock('../api/orders', () => ({ ordersApi: { get: vi.fn(), cancel: vi.fn() } }))
// Keep the real test methods and key generator; only the network calls are mocked.
vi.mock('../api/payments', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/payments')>()),
  paymentsApi: { list: vi.fn(), pay: vi.fn() },
}))
import { useAuth } from '../context/AuthContext'
import { ordersApi } from '../api/orders'
import { paymentsApi } from '../api/payments'

const DECLINED = payment({
  status: 'FAILED',
  paymentMethod: 'test_declined',
  cardBrand: null,
  cardLast4: null,
  providerReference: null,
  failureReason: 'Your card was declined.',
})

function open(state?: unknown) {
  return renderPage(<OrderDetailPage />, { path: '/orders/:id', url: '/orders/55', state })
}

beforeEach(() => {
  vi.mocked(useAuth).mockReturnValue({ userId: 7 } as unknown as ReturnType<typeof useAuth>)
  vi.mocked(ordersApi.get).mockReset().mockResolvedValue(order())
  vi.mocked(ordersApi.cancel).mockReset()
  vi.mocked(paymentsApi.list).mockReset().mockResolvedValue([])
  vi.mocked(paymentsApi.pay).mockReset()
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

  it('pays a pending order with the chosen test card, and shows it paid', async () => {
    // Like the server: once paid, fetching the order returns it paid too.
    vi.mocked(paymentsApi.pay).mockImplementation(async () => {
      vi.mocked(ordersApi.get).mockResolvedValue(order({ status: 'PAID' }))
      return payment({ cardBrand: 'Mastercard', cardLast4: '4444', paymentMethod: 'test_mastercard' })
    })
    open()

    fireEvent.change(await screen.findByLabelText('Test card'), { target: { value: 'test_mastercard' } })
    fireEvent.click(screen.getByRole('button', { name: 'Pay $48.00' }))

    await waitFor(() =>
      expect(paymentsApi.pay).toHaveBeenCalledWith(55, 'test_mastercard', expect.stringMatching(/^[\w-]{8,64}$/)),
    )
    expect(await screen.findByText('paid')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Pay/ })).not.toBeInTheDocument()
    // Paid: no cancelling any more, since nothing could refund it.
    expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument()
  })

  it('shows why a payment failed, and keeps the order payable', async () => {
    vi.mocked(paymentsApi.pay).mockResolvedValue(DECLINED)
    open()

    fireEvent.click(await screen.findByRole('button', { name: 'Pay $48.00' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Payment failed: Your card was declined.')
    expect(screen.getByRole('button', { name: 'Pay $48.00' })).toBeEnabled()
  })

  // A request lost on the network may still have paid: sending its key again gets
  // that answer instead of paying twice. Once the server has answered, the next
  // click is a new attempt with a new key.
  it('repeats an attempt with its key after a network error, and starts a new one after an answer', async () => {
    vi.mocked(paymentsApi.pay)
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValue(DECLINED)
    open()
    const payButton = await screen.findByRole('button', { name: 'Pay $48.00' })

    fireEvent.click(payButton)
    await screen.findByRole('alert')
    fireEvent.click(payButton)
    await waitFor(() => expect(paymentsApi.pay).toHaveBeenCalledTimes(2))
    await screen.findByText('Payment failed: Your card was declined.')
    fireEvent.click(payButton)
    await waitFor(() => expect(paymentsApi.pay).toHaveBeenCalledTimes(3))

    const keys = vi.mocked(paymentsApi.pay).mock.calls.map((call) => call[2])
    expect(keys[1]).toBe(keys[0])
    expect(keys[2]).not.toBe(keys[0])
  })

  it('lists the order’s payment attempts', async () => {
    vi.mocked(ordersApi.get).mockResolvedValue(order({ status: 'PAID' }))
    vi.mocked(paymentsApi.list).mockResolvedValue([DECLINED, payment({ id: 8 })])
    open()

    expect(await screen.findByText('Visa ending 4242')).toBeInTheDocument()
    expect(screen.getByText('Card that is declined')).toBeInTheDocument()
    expect(screen.getByText('Your card was declined.')).toBeInTheDocument()
    expect(screen.getByText('failed')).toBeInTheDocument()
    expect(paymentsApi.list).toHaveBeenCalledWith(55)
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
