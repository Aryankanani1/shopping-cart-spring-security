import { NavLink } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { notificationsApi } from '../api/notifications'
import { queryKeys } from '../api/queryKeys'
import { useAuth } from '../context/AuthContext'
import { cx } from '../lib/format'

// Reminders and alerts are raised by a server-side job, so there's no event to
// react to — poll the (cheap) unread count, and refresh it when the tab regains focus.
const POLL_MS = 60_000

/** Header link to the inbox, with an unread badge. Render only when signed in. */
export function NotificationBell() {
  const { userId } = useAuth()
  const { data: unread = 0 } = useQuery({
    queryKey: queryKeys.notifications.unread(userId),
    queryFn: () => notificationsApi.unreadCount(),
    enabled: userId != null,
    refetchInterval: POLL_MS,
    refetchOnWindowFocus: true,
  })

  return (
    <NavLink
      to="/notifications"
      className={({ isActive }) => cx('nav__link', isActive && 'is-active')}
      aria-label={unread > 0 ? `Alerts, ${unread} unread` : 'Alerts'}
    >
      Alerts
      {unread > 0 && <span className="cart-badge nav__badge">{unread}</span>}
    </NavLink>
  )
}
