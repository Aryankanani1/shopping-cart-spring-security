import { TEST_PAYMENT_METHODS } from '../../api/payments'
import type { PaymentDto, PaymentStatus } from '../../api/types'
import { cx, formatDateTime, formatMoney } from '../../lib/format'

const STATUS: Record<PaymentStatus, { label: string; tone?: string }> = {
  SUCCEEDED: { label: 'paid', tone: 'tag--ok' },
  FAILED: { label: 'failed', tone: 'tag--danger' },
  PROCESSING: { label: 'processing' },
  REFUND_PENDING: { label: 'refund pending' },
  REFUNDED: { label: 'refunded' },
}

// Statuses the server settles on its own, later (a refund runs after the
// cancellation commits; a payment the provider was slow to answer is reconciled):
// the list checks back until none is left.
const UNSETTLED = new Set<PaymentStatus>(['PROCESSING', 'REFUND_PENDING'])
const RECHECK_MS = 2000

/** How soon to fetch the payments again: while one is still settling, every couple of seconds; otherwise never. */
export function recheckInterval(payments: PaymentDto[] | undefined): number | false {
  return payments?.some((payment) => UNSETTLED.has(payment.status)) ? RECHECK_MS : false
}

function methodOf(payment: PaymentDto): string {
  if (payment.cardBrand) return `${payment.cardBrand} ending ${payment.cardLast4}`
  return TEST_PAYMENT_METHODS.find((m) => m.value === payment.paymentMethod)?.label ?? payment.paymentMethod
}

/** An order's payment attempts, oldest first; nothing until there is one. */
export function PaymentHistory({ payments }: { payments: PaymentDto[] | undefined }) {
  if (!payments || payments.length === 0) return null

  return (
    <div className="order-payments">
      <span className="eyebrow">Payments</span>
      <ul className="order-payments__list">
        {payments.map((payment) => (
          <li className="order-payments__item" key={payment.id}>
            <div>
              <div>{methodOf(payment)}</div>
              <div className="faint">{formatDateTime(payment.createdAt)}</div>
              {payment.failureReason && <div className="faint">{payment.failureReason}</div>}
            </div>
            <span className={cx('tag', STATUS[payment.status].tone)}>
              <span className="dot" />
              {STATUS[payment.status].label}
            </span>
            <span className="price">{formatMoney(payment.amount)}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
