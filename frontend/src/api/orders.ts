import { request } from './client'
import type { OrderDto, OrderSummaryDto, PagedResponse, SlicedResponse } from './types'

/** The order lifecycle states, in fulfillment order (mirrors the backend enum). */
export const ORDER_STATUSES = [
  'PENDING',
  'PROCESSING',
  'SHIPPED',
  'DELIVERED',
  'CANCELLED',
] as const

/**
 * Legal next states from each status — mirrors OrderStatus.canTransitionTo on the
 * backend so the admin UI only offers valid moves. The server re-validates, so a
 * stale map here can never corrupt data; it just improves the UX.
 */
export const NEXT_STATUSES: Record<string, string[]> = {
  PENDING: ['PROCESSING', 'CANCELLED'],
  PROCESSING: ['SHIPPED', 'CANCELLED'],
  SHIPPED: ['DELIVERED'],
  DELIVERED: [],
  CANCELLED: [],
}

export const ordersApi = {
  /** Turn the user's cart into an order (clears the cart server-side). */
  place: (userId: number) => request<OrderDto>('/orders', { method: 'POST', query: { userId } }),

  get: (orderId: number) => request<OrderDto>(`/orders/${orderId}`),

  /** One keyset slice of the user's order history, newest first. */
  history: (userId: number, cursor?: string | null, size = 10) =>
    request<SlicedResponse<OrderDto>>('/orders', {
      query: { userId, cursor: cursor ?? undefined, size },
    }),

  /** Admin: a page of all orders (newest first). */
  adminList: (page = 0, size = 20) =>
    request<PagedResponse<OrderSummaryDto>>('/orders/admin', { query: { page, size } }),

  /** Advance an order's lifecycle status (admin only, e.g. PROCESSING → SHIPPED). */
  updateStatus: (orderId: number, status: string) =>
    request<OrderDto>(`/orders/${orderId}/status`, { method: 'PATCH', body: { status } }),

  /** Cancel an order (owner or admin, while PENDING/PROCESSING); restocks inventory. */
  cancel: (orderId: number) => request<OrderDto>(`/orders/${orderId}/cancel`, { method: 'POST' }),
}
