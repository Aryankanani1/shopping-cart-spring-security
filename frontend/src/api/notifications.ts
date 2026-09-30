import { request } from './client'
import type { NotificationDto, PagedResponse } from './types'

// The caller's own in-app inbox (wishlist reminders + price/stock alerts).
export const notificationsApi = {
  /** Newest first. */
  list: (page: number, size: number) =>
    request<PagedResponse<NotificationDto>>('/notifications', { query: { page, size } }),

  unreadCount: () => request<number>('/notifications/unread-count'),

  markRead: (id: number) => request<null>(`/notifications/${id}/read`, { method: 'POST' }),

  markAllRead: () => request<null>('/notifications/read-all', { method: 'POST' }),

  remove: (id: number) => request<null>(`/notifications/${id}`, { method: 'DELETE' }),
}
