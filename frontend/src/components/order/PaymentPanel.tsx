import { useRef, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { newIdempotencyKey, paymentsApi, TEST_PAYMENT_METHODS } from '../../api/payments'
import { queryKeys } from '../../api/queryKeys'
import { errMessage } from '../../lib/errors'
import { formatMoney } from '../../lib/format'
import { ErrorNote } from '../ui'

/** Pay for a pending order with one of the simulated provider's test cards. */
export function PaymentPanel({ orderId, amount }: { orderId: number; amount: number }) {
  const queryClient = useQueryClient()
  const [method, setMethod] = useState<string>(TEST_PAYMENT_METHODS[0].value)
  const [failure, setFailure] = useState<string | null>(null)
  // One key per attempt. It is kept while the attempt's outcome is unknown (the
  // request failed on the network, or the server failed), so paying again repeats
  // that attempt and gets its answer instead of paying twice; it is dropped once
  // the server has answered.
  const attemptKey = useRef<string | null>(null)

  const pay = useMutation({
    mutationFn: (paymentMethod: string) => {
      attemptKey.current ??= newIdempotencyKey()
      return paymentsApi.pay(orderId, paymentMethod, attemptKey.current)
    },
    onSuccess: (payment) => {
      attemptKey.current = null
      setFailure(payment.status === 'FAILED' ? (payment.failureReason ?? 'The payment failed.') : null)
      // The order's status and its payments (under the order's key) both changed.
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.all })
    },
    onError: (err) => {
      if (err instanceof ApiError && err.status < 500) attemptKey.current = null
    },
  })

  return (
    <section className="payment-panel" aria-labelledby="payment-heading">
      <h2 id="payment-heading" className="eyebrow">
        Payment
      </h2>
      <p className="faint">
        This is a demo shop: payments are simulated, and no real card is charged. Pick a test card.
      </p>
      <div className="payment-panel__row">
        <label className="label" htmlFor="payment-method">
          Test card
        </label>
        <select
          id="payment-method"
          className="field"
          value={method}
          disabled={pay.isPending}
          onChange={(e) => {
            setMethod(e.target.value)
            attemptKey.current = null // a key names one request; another card is another attempt
          }}
        >
          {TEST_PAYMENT_METHODS.map((m) => (
            <option key={m.value} value={m.value}>
              {m.label}
            </option>
          ))}
        </select>
        <button className="btn btn--accent" onClick={() => pay.mutate(method)} disabled={pay.isPending}>
          {pay.isPending ? 'Paying…' : `Pay ${formatMoney(amount)}`}
        </button>
      </div>
      {failure && <ErrorNote message={`Payment failed: ${failure}`} />}
      {pay.isError && <ErrorNote message={errMessage(pay.error)} />}
    </section>
  )
}
