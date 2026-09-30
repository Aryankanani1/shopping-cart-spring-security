import { Link } from 'react-router-dom'
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { notificationsApi } from '../api/notifications'
import { queryKeys } from '../api/queryKeys'
import { useAuth } from '../context/AuthContext'
import { Loader, ErrorNote, EmptyState } from '../components/ui'
import { errMessage } from '../lib/errors'
import { notificationText } from '../lib/notifications'
import { cx, formatDateTime } from '../lib/format'

const PAGE = 20

export function NotificationsPage() {
  const { userId } = useAuth()
  const queryClient = useQueryClient()

  const {
    data,
    error: queryError,
    isLoading,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfiniteQuery({
    queryKey: queryKeys.notifications.list(userId),
    queryFn: ({ pageParam }) => notificationsApi.list(pageParam, PAGE),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.last ? undefined : last.number + 1),
    enabled: userId != null,
  })

  // Any change refetches the list and the header's unread badge together.
  const invalidate = () => queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all(userId) })
  const markAllRead = useMutation({ mutationFn: () => notificationsApi.markAllRead(), onSuccess: invalidate })
  const markRead = useMutation({ mutationFn: (id: number) => notificationsApi.markRead(id), onSuccess: invalidate })
  const dismiss = useMutation({ mutationFn: (id: number) => notificationsApi.remove(id), onSuccess: invalidate })

  const items = data?.pages.flatMap((page) => page.content) ?? []
  const hasUnread = items.some((n) => !n.read)
  const actionError = markAllRead.error ?? markRead.error ?? dismiss.error
  const error = queryError ?? actionError

  return (
    <div className="container section">
      <div className="page-head notices__head">
        <div>
          <span className="eyebrow">Inbox</span>
          <h1>Alerts</h1>
        </div>
        {hasUnread && (
          <button
            className="btn btn--ghost btn--sm"
            onClick={() => markAllRead.mutate()}
            disabled={markAllRead.isPending}
          >
            Mark all read
          </button>
        )}
      </div>

      {error && <ErrorNote message={errMessage(error)} />}

      {isLoading ? (
        <Loader label="Loading alerts" />
      ) : items.length === 0 ? (
        <EmptyState title="Nothing here yet.">
          <p className="muted">
            Reminders you set on your wishlist, and price drops or restocks on saved items, show up here.
          </p>
          <Link to="/wishlist" className="btn btn--accent">
            Go to your wishlist
          </Link>
        </EmptyState>
      ) : (
        <>
          <ul className="notices">
            {items.map((n) => {
              const text = notificationText(n)
              return (
                <li key={n.id} className={cx('notice card', !n.read && 'is-unread')}>
                  <div className="notice__body">
                    {!n.read && <span className="notice__dot" aria-label="Unread" />}
                    <div>
                      <p className="notice__text">{text}</p>
                      <span className="faint">{formatDateTime(n.createdAt)}</span>
                    </div>
                  </div>
                  <div className="notice__actions">
                    {n.productId != null && (
                      <Link
                        to={`/products/${n.productId}`}
                        className="link link--accent"
                        onClick={() => !n.read && markRead.mutate(n.id)}
                      >
                        View product
                      </Link>
                    )}
                    {!n.read && (
                      <button className="linkbtn" onClick={() => markRead.mutate(n.id)}>
                        Mark read
                      </button>
                    )}
                    <button
                      className="linkbtn"
                      onClick={() => dismiss.mutate(n.id)}
                      aria-label={`Dismiss: ${text}`}
                    >
                      Dismiss
                    </button>
                  </div>
                </li>
              )
            })}
          </ul>
          {hasNextPage && (
            <div className="pager">
              <button className="btn btn--ghost" onClick={() => void fetchNextPage()} disabled={isFetchingNextPage}>
                {isFetchingNextPage ? 'Loading…' : 'Load more'}
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
