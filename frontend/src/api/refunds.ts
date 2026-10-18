import { request } from './client'
import type { RefundDto, RefundOrderRequest } from './types'

export const refundsApi = {
  /** The order's refunds, oldest first (its owner or an admin). */
  list: (orderId: number) => request<RefundDto[]>(`/orders/${orderId}/refunds`),

  /**
   * Admin: refund part of a paid order, items (priced by the server at what the
   * customer paid) or an amount. The refund is PENDING until the provider has made
   * it. Sent again with the same key, it gets the first refund back instead of
   * refunding twice.
   */
  create: (orderId: number, body: RefundOrderRequest, idempotencyKey: string) =>
    request<RefundDto>(`/orders/${orderId}/refunds`, {
      method: 'POST',
      body,
      headers: { 'Idempotency-Key': idempotencyKey },
    }),
}
