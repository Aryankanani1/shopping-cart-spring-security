import { useId } from 'react'
import type { OrderItemDto, RefundDto, RefundKind, RefundStatus } from '../../api/types'
import { cx, formatDateTime, formatMoney } from '../../lib/format'

const KIND: Record<RefundKind, string> = {
  ITEMS: 'Items returned',
  AMOUNT: 'Partial refund',
  CANCELLATION: 'Order cancelled',
}

const STATUS: Record<RefundStatus, { label: string; tone?: string }> = {
  PENDING: { label: 'pending' },
  REFUNDED: { label: 'refunded', tone: 'tag--ok' },
}

const RECHECK_MS = 2000

/**
 * How soon to fetch the refunds again: the provider makes a refund just after it
 * is committed, so while one is still pending, every couple of seconds; otherwise never.
 */
export function refundRecheckInterval(refunds: RefundDto[] | undefined): number | false {
  return refunds?.some((refund) => refund.status === 'PENDING') ? RECHECK_MS : false
}

/** "1 × Desk Lamp, 2 × Kettle": what a refund of items gave money back for. */
function itemsOf(refund: RefundDto, orderItems: OrderItemDto[]): string {
  return refund.items
    .map((line) => {
      const name = orderItems.find((item) => item.id === line.orderItemId)?.productName ?? `item ${line.orderItemId}`
      return `${line.quantity} × ${name}`
    })
    .join(', ')
}

/**
 * An order's refunds, oldest first, with the admin's reason, which the customer
 * sees too; nothing until there is one.
 */
export function RefundList({ refunds, items }: { refunds: RefundDto[] | undefined; items: OrderItemDto[] }) {
  const headingId = useId()
  if (!refunds || refunds.length === 0) return null

  return (
    <section className="order-refunds" aria-labelledby={headingId}>
      <h2 id={headingId} className="eyebrow">
        Refunds
      </h2>
      <ul className="order-refunds__list">
        {refunds.map((refund) => (
          <li className="order-refunds__item" key={refund.id}>
            <div>
              <div>{KIND[refund.kind]}</div>
              {/* A cancellation's reason only repeats that the order was cancelled. */}
              {refund.reason && refund.kind !== 'CANCELLATION' && <div className="faint">{refund.reason}</div>}
              {refund.items.length > 0 && <div className="faint">{itemsOf(refund, items)}</div>}
              <div className="faint">
                {refund.refundedAt
                  ? `Refunded ${formatDateTime(refund.refundedAt)}`
                  : `Requested ${formatDateTime(refund.requestedAt)}`}
              </div>
            </div>
            <span className={cx('tag', STATUS[refund.status].tone)}>
              <span className="dot" />
              {STATUS[refund.status].label}
            </span>
            <span className="price">{formatMoney(refund.amount)}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
