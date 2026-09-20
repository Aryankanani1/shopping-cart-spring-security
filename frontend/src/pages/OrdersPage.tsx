import { Link } from 'react-router-dom'
import { useInfiniteQuery } from '@tanstack/react-query'
import { ordersApi } from '../api/orders'
import { queryKeys } from '../api/queryKeys'
import { errMessage } from '../lib/errors'
import { useAuth } from '../context/AuthContext'
import { Loader, ErrorNote, EmptyState } from '../components/ui'
import { StatusBadge } from '../components/StatusBadge'
import { formatDate, formatMoney } from '../lib/format'

const PAGE = 10

export function OrdersPage() {
  const { userId } = useAuth()

  // Keyset pagination is exactly what useInfiniteQuery models: each slice carries
  // the cursor for the next. This replaces the manual orders/cursor/hasNext/
  // loading state machine — and the pages are cached and deduped.
  const {
    data,
    error: queryError,
    isLoading: loading,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage: loadingMore,
  } = useInfiniteQuery({
    queryKey: queryKeys.orders.history(userId),
    queryFn: ({ pageParam }) => ordersApi.history(userId as number, pageParam, PAGE),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.hasNext ? last.nextCursor : undefined),
    enabled: userId != null,
  })

  const orders = data?.pages.flatMap((page) => page.content) ?? []
  const error = queryError ? errMessage(queryError) : null
  const hasNext = hasNextPage
  const loadMore = () => void fetchNextPage()

  return (
    <div className="container section">
      <div className="page-head">
        <span className="eyebrow">History</span>
        <h1>Your orders</h1>
      </div>

      {error && <ErrorNote message={error} />}

      {loading ? (
        <Loader label="Loading orders" />
      ) : orders.length === 0 ? (
        <EmptyState title="No orders yet.">
          <Link to="/products" className="btn btn--accent">
            Browse the catalogue
          </Link>
        </EmptyState>
      ) : (
        <>
          <ul className="orders">
            {orders.map((o) => (
              <li key={o.id}>
                <Link to={`/orders/${o.id}`} className="order-row card">
                  <div className="order-row__main">
                    <span className="eyebrow">Order</span>
                    <span className="order-row__id serif">#{o.id}</span>
                    <span className="faint">{formatDate(o.orderDate)}</span>
                  </div>
                  <div className="order-row__meta">
                    <StatusBadge status={o.status} />
                    <span className="faint nowrap">
                      {(o.items?.length ?? 0)} item{(o.items?.length ?? 0) === 1 ? '' : 's'}
                    </span>
                    <span className="price">{formatMoney(o.totalAmount)}</span>
                  </div>
                </Link>
              </li>
            ))}
          </ul>

          {hasNext && (
            <div className="pager">
              <button className="btn btn--ghost" onClick={loadMore} disabled={loadingMore}>
                {loadingMore ? 'Loading…' : 'Load more'}
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
