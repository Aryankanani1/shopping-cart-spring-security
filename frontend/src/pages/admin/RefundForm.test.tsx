import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { leftToRefund, RefundForm } from './RefundForm'
import { ApiError } from '../../api/client'
import { renderPage } from '../../test/renderPage'
import { order, payment, refund } from '../../test/fixtures'
import { queryKeys } from '../../api/queryKeys'

vi.mock('../../api/orders', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/orders')>()),
  ordersApi: { get: vi.fn() },
}))
// Keep the real key generator; only the network calls are mocked.
vi.mock('../../api/payments', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/payments')>()),
  paymentsApi: { list: vi.fn() },
}))
vi.mock('../../api/refunds', () => ({ refundsApi: { list: vi.fn(), create: vi.fn() } }))
import { ordersApi } from '../../api/orders'
import { paymentsApi } from '../../api/payments'
import { refundsApi } from '../../api/refunds'

// Two lamps at $24 and a kettle at $35: $83, paid.
const LAMP = { id: 101, productId: 1, productName: 'Desk Lamp', productBrand: 'Acme', quantity: 2, refundedQuantity: 0, price: 24 }
const KETTLE = { id: 102, productId: 2, productName: 'Kettle', productBrand: 'Acme', quantity: 1, refundedQuantity: 0, price: 35 }
const PAID_ORDER = order({ status: 'DELIVERED', totalAmount: 83, items: [LAMP, KETTLE] })
const PAID = payment({ amount: 83 })

const quantityOf = (name: string) => screen.getByLabelText(`How many ${name} to refund`)
const reason = () => screen.getByLabelText('Reason (the customer sees it)')

function open() {
  return renderPage(<RefundForm orderId={55} />)
}

beforeEach(() => {
  vi.spyOn(window, 'confirm').mockReturnValue(true)
  vi.mocked(ordersApi.get).mockReset().mockResolvedValue(PAID_ORDER)
  vi.mocked(paymentsApi.list).mockReset().mockResolvedValue([PAID])
  vi.mocked(refundsApi.list).mockReset().mockResolvedValue([])
  vi.mocked(refundsApi.create).mockReset()
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('RefundForm', () => {
  it('refunds items sent back at what was paid for them, and can put them back in stock', async () => {
    vi.mocked(refundsApi.create).mockResolvedValue(refund({ kind: 'ITEMS', amount: 24, status: 'PENDING' }))
    const { queryClient } = open()
    await screen.findByText(/\$83\.00 of \$83\.00 is left to refund/)
    queryClient.setQueryData(queryKeys.products.detail(1), {})

    fireEvent.change(quantityOf('Desk Lamp'), { target: { value: '1' } })
    fireEvent.click(screen.getByLabelText('Put them back in stock'))
    fireEvent.change(reason(), { target: { value: 'Returned: wrong colour' } })
    fireEvent.click(screen.getByRole('button', { name: 'Refund $24.00' }))

    await waitFor(() =>
      expect(refundsApi.create).toHaveBeenCalledWith(
        55,
        { items: [{ orderItemId: 101, quantity: 1 }], restock: true, reason: 'Returned: wrong colour' },
        expect.stringMatching(/^[\w-]{8,64}$/),
      ),
    )
    expect(await screen.findByRole('status')).toHaveTextContent('Refund of $24.00 requested.')
    // The order (its refunded quantities and refunds) and, restocked, the catalogue.
    await waitFor(() => expect(ordersApi.get).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(queryClient.getQueryState(queryKeys.products.detail(1))?.isInvalidated).toBe(true))
  })

  it('refunds an amount, but no more than is left', async () => {
    vi.mocked(refundsApi.list).mockResolvedValue([refund({ amount: 30 })])
    vi.mocked(refundsApi.create).mockResolvedValue(refund({ amount: 12.5, status: 'PENDING' }))
    open()
    await screen.findByText(/\$53\.00 of \$83\.00 is left to refund/)

    fireEvent.click(screen.getByLabelText('An amount'))
    fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '60' } })
    fireEvent.change(reason(), { target: { value: 'The box arrived damaged' } })
    expect(screen.getByRole('alert')).toHaveTextContent('Only $53.00 is left to refund.')
    expect(screen.getByRole('button', { name: 'Refund $60.00' })).toBeDisabled()

    fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '12.50' } })
    fireEvent.click(screen.getByRole('button', { name: 'Refund $12.50' }))

    await waitFor(() =>
      expect(refundsApi.create).toHaveBeenCalledWith(55, { amount: 12.5, reason: 'The box arrived damaged' }, expect.any(String)),
    )
  })

  it('offers only what is left of each item', async () => {
    vi.mocked(ordersApi.get).mockResolvedValue({ ...PAID_ORDER, items: [{ ...LAMP, refundedQuantity: 2 }, KETTLE] })
    vi.mocked(refundsApi.list).mockResolvedValue([
      refund({ kind: 'ITEMS', amount: 48, items: [{ orderItemId: 101, quantity: 2, amount: 48 }] }),
    ])
    open()
    await screen.findByText(/\$35\.00 of \$83\.00 is left to refund/)

    expect(quantityOf('Desk Lamp')).toBeDisabled()
    fireEvent.change(quantityOf('Kettle'), { target: { value: '5' } })
    expect(quantityOf('Kettle')).toHaveValue(1)
  })

  it('needs something to refund, and a reason', async () => {
    open()
    await screen.findByText(/left to refund/)

    expect(screen.getByRole('button', { name: 'Refund $0.00' })).toBeDisabled()
    fireEvent.change(quantityOf('Kettle'), { target: { value: '1' } })
    expect(screen.getByRole('button', { name: 'Refund $35.00' })).toBeDisabled()
    fireEvent.change(reason(), { target: { value: '   ' } })
    expect(screen.getByRole('button', { name: 'Refund $35.00' })).toBeDisabled()
    fireEvent.change(reason(), { target: { value: 'Returned' } })
    expect(screen.getByRole('button', { name: 'Refund $35.00' })).toBeEnabled()
  })

  it('asks first, and refunds nothing if the admin backs out', async () => {
    vi.mocked(window.confirm).mockReturnValue(false)
    open()
    await screen.findByText(/left to refund/)

    fireEvent.change(quantityOf('Kettle'), { target: { value: '1' } })
    fireEvent.change(reason(), { target: { value: 'Returned' } })
    fireEvent.click(screen.getByRole('button', { name: 'Refund $35.00' }))

    expect(window.confirm).toHaveBeenCalledWith('Refund $35.00 to the customer? This can’t be undone.')
    expect(refundsApi.create).not.toHaveBeenCalled()
  })

  it('shows a refused refund, and fetches the order again', async () => {
    vi.mocked(refundsApi.create).mockRejectedValue(
      new ApiError(409, 'Only 10.00 USD is left to refund, not 35.00'),
    )
    open()
    await screen.findByText(/left to refund/)

    fireEvent.change(quantityOf('Kettle'), { target: { value: '1' } })
    fireEvent.change(reason(), { target: { value: 'Returned' } })
    fireEvent.click(screen.getByRole('button', { name: 'Refund $35.00' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Only 10.00 USD is left to refund')
    await waitFor(() => expect(refundsApi.list).toHaveBeenCalledTimes(2))
  })

  // As for a payment: a request lost on the network may still have refunded, and
  // sending its key again gets that refund back instead of a second one. Once the
  // server has answered, or the admin changes the refund, the next is a new key.
  it('repeats a refund with its key after a network error, and uses a new key once answered', async () => {
    vi.mocked(refundsApi.create)
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValue(refund({ kind: 'ITEMS', amount: 35, status: 'PENDING' }))
    open()
    await screen.findByText(/left to refund/)
    const fill = () => {
      fireEvent.change(quantityOf('Kettle'), { target: { value: '1' } })
      fireEvent.change(reason(), { target: { value: 'Returned' } })
    }

    fill()
    fireEvent.click(screen.getByRole('button', { name: 'Refund $35.00' }))
    await screen.findByRole('alert')
    fireEvent.click(screen.getByRole('button', { name: 'Refund $35.00' }))
    await screen.findByText('Refund of $35.00 requested.')
    fill()
    fireEvent.click(screen.getByRole('button', { name: 'Refund $35.00' }))
    await waitFor(() => expect(refundsApi.create).toHaveBeenCalledTimes(3))

    const keys = vi.mocked(refundsApi.create).mock.calls.map((call) => call[2])
    expect(keys[1]).toBe(keys[0])
    expect(keys[2]).not.toBe(keys[0])
  })

  it('uses a new key once the refund is changed', async () => {
    vi.mocked(refundsApi.create)
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValue(refund({ kind: 'ITEMS', amount: 59, status: 'PENDING' }))
    open()
    await screen.findByText(/left to refund/)

    fireEvent.change(quantityOf('Kettle'), { target: { value: '1' } })
    fireEvent.change(reason(), { target: { value: 'Returned' } })
    fireEvent.click(screen.getByRole('button', { name: 'Refund $35.00' }))
    await screen.findByRole('alert')
    fireEvent.change(quantityOf('Desk Lamp'), { target: { value: '1' } })
    fireEvent.click(screen.getByRole('button', { name: 'Refund $59.00' }))
    await waitFor(() => expect(refundsApi.create).toHaveBeenCalledTimes(2))

    const keys = vi.mocked(refundsApi.create).mock.calls.map((call) => call[2])
    expect(keys[1]).not.toBe(keys[0])
  })

  it('says why an order can’t be refunded', async () => {
    vi.mocked(paymentsApi.list).mockResolvedValue([payment({ status: 'REFUND_PENDING' })])
    open()

    expect(await screen.findByText('The order was cancelled: its payment is being refunded in full.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Refund/ })).not.toBeInTheDocument()
  })

  it('lists the refunds so far, and says when nothing is left', async () => {
    vi.mocked(refundsApi.list).mockResolvedValue([refund({ amount: 50 }), refund({ id: 4, amount: 33 })])
    open()

    expect(await screen.findByText('Nothing is left to refund.')).toBeInTheDocument()
    expect(screen.getAllByText('Partial refund')).toHaveLength(2)
    expect(screen.queryByRole('button', { name: /^Refund/ })).not.toBeInTheDocument()
  })
})

describe('leftToRefund', () => {
  it('counts in cents, so refunds add up exactly', () => {
    // In floating point, 0.1 + 0.2 is 0.30000000000000004: more than the 0.3 paid.
    const paid = payment({ amount: 0.3 })
    expect(leftToRefund(paid, [refund({ amount: 0.1 }), refund({ amount: 0.2 })])).toBe(0)
  })

  it('counts only the refunds of that payment, owed or made', () => {
    const refunds = [refund({ amount: 10, status: 'PENDING' }), refund({ amount: 5 }), refund({ paymentId: 9, amount: 40 })]
    expect(leftToRefund(payment({ amount: 83 }), refunds)).toBe(6800)
  })
})
