import { request } from './client'
import type { WishlistItemDto } from './types'

// The caller's own wishlist — no user id anywhere; items are keyed by product id.
export const wishlistApi = {
  list: () => request<WishlistItemDto[]>('/wishlist'),

  /** Idempotent: saving an already-saved product just returns it. */
  add: (productId: number) => request<WishlistItemDto>(`/wishlist/items/${productId}`, { method: 'PUT' }),

  remove: (productId: number) => request<null>(`/wishlist/items/${productId}`, { method: 'DELETE' }),

  /** `remindAt` is an ISO instant in the future. */
  setReminder: (productId: number, remindAt: string) =>
    request<WishlistItemDto>(`/wishlist/items/${productId}/reminder`, { method: 'PUT', body: { remindAt } }),

  clearReminder: (productId: number) =>
    request<WishlistItemDto>(`/wishlist/items/${productId}/reminder`, { method: 'DELETE' }),

  setAlerts: (productId: number, enabled: boolean) =>
    request<WishlistItemDto>(`/wishlist/items/${productId}/alerts`, { method: 'PUT', body: { enabled } }),
}
