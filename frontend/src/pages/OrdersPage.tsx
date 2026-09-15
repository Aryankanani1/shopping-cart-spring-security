import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ordersApi } from '../api/orders'
import type { OrderDto } from '../api/types'
import { useAuth } from '../context/AuthContext'
import { Loader, ErrorNote, EmptyState } from '../components/ui'
import { StatusBadge } from '../components/StatusBadge'
import { errMessage } from '../hooks/useAsync'
import { formatDate, formatMoney } from '../lib/format'

const PAGE = 10

export function OrdersPage() {
  const { userId } = useAuth()
  const [orders, setOrders] = useState<OrderDto[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasNext, setHasNext] = useState(false)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Initial slice — newest first. Cursor pagination, so no total to fetch.
  useEffect(() => {
    if (!userId) return
    let active = true
    setLoading(true)
    setError(null)
    ordersApi.history(userId, null, PAGE).then(
      (slice) => {
        if (!active) return
        setOrders(slice.content)
        setCursor(slice.nextCursor)
        setHasNext(slice.hasNext)
        setLoading(false)
      },
      (err) => {
        if (!active) return
        setError(errMessage(err))
        setLoading(false)
      },
    )
    return () => {
      active = false
    }
  }, [userId])

  async function loadMore() {
    if (!userId || !hasNext) return
    setLoadingMore(true)
    setError(null)
    try {
      const slice = await ordersApi.history(userId, cursor, PAGE)
      setOrders((prev) => [...prev, ...slice.content])
      setCursor(slice.nextCursor)
      setHasNext(slice.hasNext)
    } catch (err) {
      setError(errMessage(err))
    } finally {
      setLoadingMore(false)
    }
  }

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
