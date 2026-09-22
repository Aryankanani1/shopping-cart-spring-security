import { request } from './client'
import type { OrderDto, SlicedResponse } from './types'

export const ordersApi = {
  /** Turn the user's cart into an order (clears the cart server-side). */
  place: (userId: number) => request<OrderDto>('/orders', { method: 'POST', query: { userId } }),

  get: (orderId: number) => request<OrderDto>(`/orders/${orderId}`),

  /** One keyset slice of the user's order history, newest first. */
  history: (userId: number, cursor?: string | null, size = 10) =>
    request<SlicedResponse<OrderDto>>('/orders', {
      query: { userId, cursor: cursor ?? undefined, size },
    }),

  /** Advance an order's lifecycle status (admin only, e.g. PROCESSING → SHIPPED). */
  updateStatus: (orderId: number, status: string) =>
    request<OrderDto>(`/orders/${orderId}/status`, { method: 'PATCH', body: { status } }),

  /** Cancel an order (owner or admin, while PENDING/PROCESSING); restocks inventory. */
  cancel: (orderId: number) => request<OrderDto>(`/orders/${orderId}/cancel`, { method: 'POST' }),
}
