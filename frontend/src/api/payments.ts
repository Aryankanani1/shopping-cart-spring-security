import { request } from './client'
import type { PaymentDto } from './types'

/**
 * The simulated provider's test payment methods. This is a demo shop: no real
 * card is charged, and each method stands in for a card with a known outcome.
 */
export const TEST_PAYMENT_METHODS = [
  { value: 'test_visa', label: 'Visa ending 4242 (approved)' },
  { value: 'test_mastercard', label: 'Mastercard ending 4444 (approved)' },
  { value: 'test_declined', label: 'Card that is declined' },
  { value: 'test_insufficient_funds', label: 'Card with insufficient funds' },
  { value: 'test_flaky', label: 'Provider fails once, then approves' },
  { value: 'test_provider_down', label: 'Provider is down' },
  { value: 'test_refund_down', label: 'Visa whose refunds fail at first' },
  { value: 'test_timeout_charged', label: 'Provider times out after charging' },
  { value: 'test_timeout_not_charged', label: 'Provider times out before charging' },
] as const

/**
 * A key naming one payment attempt. Sent again with a repeated request (after a
 * network error, say), it gets the first answer instead of paying twice.
 */
export function newIdempotencyKey(): string {
  // randomUUID needs a secure context (HTTPS or localhost); getRandomValues doesn't.
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
}

export const paymentsApi = {
  /** The order's payment attempts, oldest first. */
  list: (orderId: number) => request<PaymentDto[]>(`/orders/${orderId}/payments`),

  /** Pay for the order; the server charges its total. A declined card resolves with status FAILED. */
  pay: (orderId: number, paymentMethod: string, idempotencyKey: string) =>
    request<PaymentDto>(`/orders/${orderId}/payments`, {
      method: 'POST',
      body: { paymentMethod },
      headers: { 'Idempotency-Key': idempotencyKey },
    }),
}
