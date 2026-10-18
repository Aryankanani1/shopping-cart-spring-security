import { describe, it, expect } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import { RefundList, refundRecheckInterval } from './RefundList'
import { order, refund } from '../../test/fixtures'

const ITEMS = order().items // a Desk Lamp, order item 101

describe('RefundList', () => {
  it('lists each refund with what it was for, the admin’s reason, and where it stands', () => {
    render(
      <RefundList
        items={ITEMS}
        refunds={[
          refund({
            id: 1,
            kind: 'ITEMS',
            amount: 24,
            reason: 'Returned: wrong colour',
            items: [{ orderItemId: 101, quantity: 1, amount: 24 }],
          }),
          refund({ id: 2, status: 'PENDING', refundedAt: null }),
        ]}
      />,
    )

    const [items, amount] = within(screen.getByRole('region', { name: 'Refunds' })).getAllByRole('listitem')
    expect(within(items).getByText('Items returned')).toBeInTheDocument()
    expect(within(items).getByText('Returned: wrong colour')).toBeInTheDocument()
    expect(within(items).getByText('1 × Desk Lamp')).toBeInTheDocument()
    expect(within(items).getByText('refunded')).toBeInTheDocument()
    expect(within(items).getByText(/^Refunded /)).toBeInTheDocument()
    expect(within(items).getByText('$24.00')).toBeInTheDocument()
    expect(within(amount).getByText('Partial refund')).toBeInTheDocument()
    expect(within(amount).getByText('The box arrived damaged')).toBeInTheDocument()
    expect(within(amount).getByText('pending')).toBeInTheDocument()
    expect(within(amount).getByText(/^Requested /)).toBeInTheDocument()
    expect(within(amount).getByText('$5.00')).toBeInTheDocument()
  })

  it('doesn’t repeat a cancellation’s reason', () => {
    render(<RefundList items={ITEMS} refunds={[refund({ kind: 'CANCELLATION', reason: 'Order cancelled', amount: 48 })]} />)

    // Once, as what the refund was for; getByText would throw on a second.
    expect(screen.getByText('Order cancelled')).toBeInTheDocument()
  })

  it('shows nothing until there is a refund', () => {
    const { container } = render(<RefundList items={ITEMS} refunds={[]} />)

    expect(container).toBeEmptyDOMElement()
  })
})

describe('refundRecheckInterval', () => {
  it('checks back while a refund is still pending', () => {
    expect(refundRecheckInterval([refund(), refund({ status: 'PENDING', refundedAt: null })])).toBe(2000)
  })

  it('stops once every refund is made, or before anything has loaded', () => {
    expect(refundRecheckInterval([refund()])).toBe(false)
    expect(refundRecheckInterval([])).toBe(false)
    expect(refundRecheckInterval(undefined)).toBe(false)
  })
})
