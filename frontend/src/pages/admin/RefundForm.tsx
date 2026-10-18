import { useId, useRef, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { ordersApi } from '../../api/orders'
import { newIdempotencyKey, paymentsApi } from '../../api/payments'
import { queryKeys } from '../../api/queryKeys'
import { refundsApi } from '../../api/refunds'
import type { PaymentDto, RefundDto, RefundOrderRequest } from '../../api/types'
import { errMessage } from '../../lib/errors'
import { formatMoney } from '../../lib/format'
import { ErrorNote, Loader } from '../../components/ui'
import { RefundList, refundRecheckInterval } from '../../components/order/RefundList'

/** Money in whole cents, so sums and comparisons are exact (0.1 + 0.2 isn't 0.3). */
const cents = (value: number) => Math.round(value * 100)
const money = (inCents: number) => formatMoney(inCents / 100)

/** An amount as typed: positive, with at most two decimals. */
const AMOUNT = /^\d+(\.\d{1,2})?$/

/**
 * What is left to refund of a payment, in cents: what was paid, less every refund
 * owed or made from it (the server keeps the same total, and refuses more).
 */
export function leftToRefund(payment: PaymentDto, refunds: RefundDto[]): number {
  const refunded = refunds
    .filter((refund) => refund.paymentId === payment.id)
    .reduce((sum, refund) => sum + cents(refund.amount), 0)
  return Math.max(0, cents(payment.amount) - refunded)
}

/** Why an order without a payment that succeeded can't be refunded (Payment.requireRefundable). */
function notRefundable(payments: PaymentDto[]): string {
  if (payments.some((payment) => payment.status === 'REFUND_PENDING')) {
    return 'The order was cancelled: its payment is being refunded in full.'
  }
  if (payments.some((payment) => payment.status === 'REFUNDED')) return 'The payment has been refunded in full.'
  return 'The order isn’t paid.'
}

/**
 * An admin's refund of part of a paid order (docs/payment-design.md, section 11):
 * items the customer sent back, which the server prices at what the customer paid
 * for them, or an amount; with a reason the customer sees. Below it, the order's
 * refunds so far.
 */
export function RefundForm({ orderId }: { orderId: number }) {
  const queryClient = useQueryClient()
  const kindName = useId()

  const order = useQuery({ queryKey: queryKeys.orders.detail(orderId), queryFn: () => ordersApi.get(orderId) })
  const payments = useQuery({
    queryKey: queryKeys.orders.payments(orderId),
    queryFn: () => paymentsApi.list(orderId),
  })
  const refunds = useQuery({
    queryKey: queryKeys.orders.refunds(orderId),
    queryFn: () => refundsApi.list(orderId),
    refetchInterval: (query) => refundRecheckInterval(query.state.data),
  })

  const [kind, setKind] = useState<'items' | 'amount'>('items')
  const [quantities, setQuantities] = useState<Record<number, number>>({})
  const [restock, setRestock] = useState(false)
  const [amount, setAmount] = useState('')
  const [reason, setReason] = useState('')
  const [requested, setRequested] = useState<string | null>(null)
  // One key per refund, as for a payment: kept while the outcome is unknown (the
  // request failed on the network, or the server failed), so sending it again gets
  // the first refund back instead of refunding twice; dropped once the server has
  // answered, and when the form changes, since the key then names another refund.
  const attemptKey = useRef<string | null>(null)

  const refund = useMutation({
    mutationFn: (body: RefundOrderRequest) => {
      attemptKey.current ??= newIdempotencyKey()
      return refundsApi.create(orderId, body, attemptKey.current)
    },
    onSuccess: (created, body) => {
      attemptKey.current = null
      setQuantities({})
      setRestock(false)
      setAmount('')
      setReason('')
      setRequested(`Refund of ${formatMoney(created.amount)} requested.`)
      // The order's refunded quantities, refunds and payment, and the admin list.
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.all })
      if ('items' in body && body.restock) queryClient.invalidateQueries({ queryKey: queryKeys.products.all })
    },
    onError: (err) => {
      if (err instanceof ApiError && err.status < 500) attemptKey.current = null
      // Another admin may have refunded meanwhile, or this one may have gone through.
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.detail(orderId) })
    },
  })

  /** Any change makes another refund: a new key, and the last outcome is old news. */
  function edit<T>(set: (value: T) => void) {
    return (value: T) => {
      set(value)
      attemptKey.current = null
      setRequested(null)
    }
  }

  if (order.isLoading || payments.isLoading || refunds.isLoading) return <Loader label="Loading the order" />
  const loadError = order.error ?? payments.error ?? refunds.error
  if (loadError) return <ErrorNote message={errMessage(loadError)} />
  if (!order.data || !payments.data || !refunds.data) return null

  const items = order.data.items ?? []
  const paid = payments.data.find((payment) => payment.status === 'SUCCEEDED')
  const left = paid ? leftToRefund(paid, refunds.data) : 0
  const history = <RefundList refunds={refunds.data} items={items} />

  if (!paid || left === 0) {
    return (
      <div className="admin-form">
        <h3 className="admin-form__title serif">Refund order #{orderId}</h3>
        <p className="faint">{paid ? 'Nothing is left to refund.' : notRefundable(payments.data)}</p>
        {history}
      </div>
    )
  }

  const amountCents = AMOUNT.test(amount.trim()) ? cents(Number(amount)) : 0
  const total =
    kind === 'items'
      ? items.reduce((sum, item) => sum + (quantities[item.id] ?? 0) * cents(item.price), 0)
      : amountCents
  const tooMuch = total > left
  const canRefund = total > 0 && !tooMuch && reason.trim() !== '' && !refund.isPending

  const setQuantity = (itemId: number, value: string, most: number) =>
    edit(setQuantities)({ ...quantities, [itemId]: Math.min(most, Math.max(0, Math.floor(Number(value) || 0))) })

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!canRefund || !window.confirm(`Refund ${money(total)} to the customer? This can’t be undone.`)) return
    refund.mutate(
      kind === 'items'
        ? {
            items: items
              .filter((item) => (quantities[item.id] ?? 0) > 0)
              .map((item) => ({ orderItemId: item.id, quantity: quantities[item.id] })),
            restock,
            reason: reason.trim(),
          }
        : { amount: amountCents / 100, reason: reason.trim() },
    )
  }

  return (
    <form className="admin-form" onSubmit={submit} aria-label={`Refund order #${orderId}`}>
      <h3 className="admin-form__title serif">Refund order #{orderId}</h3>
      <p className="faint">
        {money(left)} of {formatMoney(paid.amount)} is left to refund. Items are refunded at what the customer paid
        for them.
      </p>

      <fieldset className="refund-form__kinds">
        <legend className="admin-field__label">Refund</legend>
        <label className="refund-form__choice">
          <input
            type="radio"
            name={kindName}
            checked={kind === 'items'}
            onChange={() => edit(setKind)('items')}
          />
          Items sent back
        </label>
        <label className="refund-form__choice">
          <input
            type="radio"
            name={kindName}
            checked={kind === 'amount'}
            onChange={() => edit(setKind)('amount')}
          />
          An amount
        </label>
      </fieldset>

      {kind === 'items' ? (
        <>
          <div className="admin-table-wrap refund-form__items">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Item</th>
                  <th className="admin-table__num">Bought</th>
                  <th className="admin-table__num">Paid each</th>
                  <th>Refund</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => {
                  const refundable = item.quantity - item.refundedQuantity
                  return (
                    <tr key={item.id}>
                      <td>
                        {item.productName}
                        {item.refundedQuantity > 0 && (
                          <span className="faint"> · {item.refundedQuantity} refunded</span>
                        )}
                      </td>
                      <td className="admin-table__num">{item.quantity}</td>
                      <td className="admin-table__num price">{formatMoney(item.price)}</td>
                      <td>
                        <input
                          className="field"
                          type="number"
                          min={0}
                          max={refundable}
                          step={1}
                          value={quantities[item.id] ?? 0}
                          disabled={refundable === 0}
                          aria-label={`How many ${item.productName} to refund`}
                          onChange={(e) => setQuantity(item.id, e.target.value, refundable)}
                        />
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <label className="refund-form__choice">
            <input type="checkbox" checked={restock} onChange={(e) => edit(setRestock)(e.target.checked)} />
            Put them back in stock
          </label>
        </>
      ) : (
        <label className="admin-field refund-form__amount">
          <span className="admin-field__label">Amount</span>
          <input
            className="field"
            type="number"
            min="0.01"
            step="0.01"
            max={left / 100}
            value={amount}
            onChange={(e) => edit(setAmount)(e.target.value)}
          />
        </label>
      )}

      <label className="admin-field refund-form__reason">
        <span className="admin-field__label">Reason (the customer sees it)</span>
        <input
          className="field"
          value={reason}
          maxLength={255}
          onChange={(e) => edit(setReason)(e.target.value)}
          required
        />
      </label>

      {tooMuch && <ErrorNote message={`Only ${money(left)} is left to refund.`} />}
      {refund.isError && <ErrorNote message={errMessage(refund.error)} />}
      {requested && (
        <p className="note note--ok" role="status">
          {requested}
        </p>
      )}

      <div className="admin-form__actions">
        <button className="btn btn--accent" type="submit" disabled={!canRefund}>
          {refund.isPending ? 'Refunding…' : `Refund ${money(total)}`}
        </button>
      </div>

      {history}
    </form>
  )
}
