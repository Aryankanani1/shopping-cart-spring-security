import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../context/AuthContext'
import { NotificationsPage } from './NotificationsPage'
import { setSession } from '../api/tokenStore'
import type { NotificationDto, PagedResponse } from '../api/types'

vi.mock('../api/notifications', () => ({
  notificationsApi: {
    list: vi.fn(),
    unreadCount: vi.fn(),
    markRead: vi.fn(),
    markAllRead: vi.fn(),
    remove: vi.fn(),
  },
}))

import { notificationsApi } from '../api/notifications'

const NOTIFICATIONS: NotificationDto[] = [
  {
    id: 3,
    type: 'BACK_IN_STOCK',
    productId: 6,
    productName: 'Desk Lamp',
    createdAt: '2026-10-01T09:03:00Z',
    read: false,
  },
  {
    id: 2,
    type: 'PRICE_DROP',
    productId: 6,
    productName: 'Desk Lamp',
    oldPrice: 40,
    newPrice: 32.5,
    createdAt: '2026-10-01T09:02:00Z',
    read: false,
  },
  {
    id: 1,
    type: 'WISHLIST_REMINDER',
    productId: null, // product since deleted
    productName: 'Enamel Kettle',
    createdAt: '2026-10-01T09:01:00Z',
    read: true,
  },
]

function page(content: NotificationDto[]): PagedResponse<NotificationDto> {
  return {
    content,
    number: 0,
    size: 20,
    totalElements: content.length,
    totalPages: 1,
    first: true,
    last: true,
    numberOfElements: content.length,
  }
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter>
          <NotificationsPage />
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  localStorage.clear()
  setSession({ id: 7, token: 't', refreshToken: 'r' })
  Object.values(notificationsApi).forEach((fn) => vi.mocked(fn).mockReset())
  vi.mocked(notificationsApi.list).mockResolvedValue(page(NOTIFICATIONS))
  vi.mocked(notificationsApi.markAllRead).mockResolvedValue(null)
  vi.mocked(notificationsApi.remove).mockResolvedValue(null)
})

describe('NotificationsPage', () => {
  it('writes each alert as a sentence', async () => {
    renderPage()
    expect(await screen.findByText('Back in stock: Desk Lamp is available again.')).toBeInTheDocument()
    expect(screen.getByText('Price drop: Desk Lamp is now $32.50 (was $40.00).')).toBeInTheDocument()
    expect(screen.getByText('Reminder: Enamel Kettle is still on your wishlist.')).toBeInTheDocument()
  })

  it('links to the product only while it still exists', async () => {
    renderPage()
    await screen.findByText(/Back in stock/)
    // Two alerts point at the lamp; the kettle's was deleted, so it has no link.
    expect(screen.getAllByRole('link', { name: 'View product' })).toHaveLength(2)
  })

  it('marks everything read', async () => {
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Mark all read' }))
    await waitFor(() => expect(notificationsApi.markAllRead).toHaveBeenCalled())
  })

  it('dismisses a single alert', async () => {
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: /Dismiss: Reminder: Enamel Kettle/ }))
    await waitFor(() => expect(notificationsApi.remove).toHaveBeenCalledWith(1))
  })

  it('shows an empty state with no alerts', async () => {
    vi.mocked(notificationsApi.list).mockResolvedValue(page([]))
    renderPage()
    expect(await screen.findByText('Nothing here yet.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Mark all read' })).not.toBeInTheDocument()
  })
})
