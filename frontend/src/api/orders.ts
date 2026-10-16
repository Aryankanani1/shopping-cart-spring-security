import { request } from './client'
import type { OrderDto, OrderSummaryDto, PagedResponse, ShippingAddress, SlicedResponse } from './types'

/** The order lifecycle states, in fulfillment order (mirrors the backend enum). */
export const ORDER_STATUSES = [
  'PENDING',
  'PAID',
  'PROCESSING',
  'SHIPPED',
  'DELIVERED',
  'CANCELLED',
] as const

/**
 * The next states an admin may set from each status — OrderStatus.canTransitionTo
 * on the backend, minus PAID, which only a successful payment sets. Keeps the
 * admin UI to valid moves; the server re-validates, so a stale map here can never
 * corrupt data, it just improves the UX.
 */
export const NEXT_STATUSES: Record<string, string[]> = {
  PENDING: ['CANCELLED'],
  PAID: ['PROCESSING'],
  PROCESSING: ['SHIPPED'],
  SHIPPED: ['DELIVERED'],
  DELIVERED: [],
  CANCELLED: [],
}

export const ordersApi = {
  /** Turn the signed-in user's cart into an order shipped to `address` (clears the cart server-side). */
  place: (address: ShippingAddress) => request<OrderDto>('/orders', { method: 'POST', body: address }),

  get: (orderId: number) => request<OrderDto>(`/orders/${orderId}`),

  /** One keyset slice of the signed-in user's order history, newest first. */
  history: (cursor?: string | null, size = 10) =>
    request<SlicedResponse<OrderDto>>('/orders', {
      query: { cursor: cursor ?? undefined, size },
    }),

  /** Admin: a page of all orders (newest first). */
  adminList: (page = 0, size = 20) =>
    request<PagedResponse<OrderSummaryDto>>('/orders/admin', { query: { page, size } }),

  /** Advance an order's lifecycle status (admin only, e.g. PROCESSING → SHIPPED). */
  updateStatus: (orderId: number, status: string) =>
    request<OrderDto>(`/orders/${orderId}/status`, { method: 'PATCH', body: { status } }),

  /** Cancel an order (owner or admin, while unpaid); restocks inventory. */
  cancel: (orderId: number) => request<OrderDto>(`/orders/${orderId}/cancel`, { method: 'POST' }),
}
