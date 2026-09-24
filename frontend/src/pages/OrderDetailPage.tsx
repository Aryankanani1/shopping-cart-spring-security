import { Link, useLocation, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ordersApi } from '../api/orders'
import { queryKeys } from '../api/queryKeys'
import { errMessage } from '../lib/errors'
import { Loader, ErrorNote } from '../components/ui'
import { StatusBadge } from '../components/StatusBadge'
import { formatDate, formatMoney } from '../lib/format'

// Statuses from which the owner may still cancel — mirrors the server's rule
// (PENDING/PROCESSING only; once SHIPPED it can no longer be cancelled).
const CANCELLABLE = new Set(['PENDING', 'PROCESSING'])

export function OrderDetailPage() {
  const { id } = useParams()
  const orderId = Number(id)
  const location = useLocation()
  const justPlaced = (location.state as { justPlaced?: boolean } | null)?.justPlaced ?? false
  const queryClient = useQueryClient()

  const {
    data: order,
    isLoading: loading,
    error: queryError,
  } = useQuery({
    queryKey: queryKeys.orders.detail(orderId),
    queryFn: () => ordersApi.get(orderId),
    enabled: Number.isFinite(orderId),
  })
  const error = queryError ? errMessage(queryError) : null

  const cancel = useMutation({
    mutationFn: () => ordersApi.cancel(orderId),
    onSuccess: (updated) => {
      // Push the returned order into the detail cache and refresh any history list.
      queryClient.setQueryData(queryKeys.orders.detail(orderId), updated)
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.all })
    },
  })

  if (loading) {
    return (
      <div className="container section">
        <Loader label="Loading order" />
      </div>
    )
  }
  if (error || !order) {
    return (
      <div className="container section">
        <ErrorNote message={error ?? 'Order not found.'} />
        <p style={{ marginTop: '1rem' }}>
          <Link to="/orders" className="link link--accent">
            ← Back to orders
          </Link>
        </p>
      </div>
    )
  }

  return (
    <div className="container section">
      <Link to="/orders" className="link pdp__back">
        ← Orders
      </Link>

      {justPlaced && (
        <p className="note note--ok" role="status">
          Thank you — your order has been placed.
        </p>
      )}

      <div className="order-head">
        <div>
          <span className="eyebrow">Order</span>
          <h1>#{order.id}</h1>
          <p className="faint">{formatDate(order.orderDate)}</p>
        </div>
        <div className="order-head__meta">
          <StatusBadge status={order.status} />
          <span className="price order-head__total">{formatMoney(order.totalAmount)}</span>
          {CANCELLABLE.has(order.status) && (
            <button
              className="linkbtn"
              onClick={() => {
                if (window.confirm('Cancel this order? Items will be returned to stock.')) {
                  cancel.mutate()
                }
              }}
              disabled={cancel.isPending}
            >
              {cancel.isPending ? 'Cancelling…' : 'Cancel order'}
            </button>
          )}
        </div>
      </div>

      {cancel.isError && <ErrorNote message={errMessage(cancel.error)} />}

      <ul className="order-items">
        <li className="order-item order-item--head">
          <span className="eyebrow">Item</span>
          <span className="eyebrow">Qty</span>
          <span className="eyebrow">Unit</span>
          <span className="eyebrow order-item__right">Total</span>
        </li>
        {(order.items ?? []).map((item) => (
          <li className="order-item" key={item.productId}>
            <div className="order-item__info">
              <span className="order-item__name serif">{item.productName}</span>
              {item.productBrand && <span className="faint">{item.productBrand}</span>}
            </div>
            <span>{item.quantity}</span>
            <span className="price">{formatMoney(item.price)}</span>
            <span className="price order-item__right">{formatMoney(item.price * item.quantity)}</span>
          </li>
        ))}
      </ul>

      {order.addressLine1 && (
        <div className="order-address">
          <span className="eyebrow">Shipping to</span>
          <address className="order-address__body">
            {order.recipientName && <div>{order.recipientName}</div>}
            <div>{order.addressLine1}</div>
            {order.addressLine2 && <div>{order.addressLine2}</div>}
            <div>
              {[order.city, order.state, order.postalCode].filter(Boolean).join(', ')}
            </div>
            {order.country && <div>{order.country}</div>}
          </address>
        </div>
      )}
    </div>
  )
}
