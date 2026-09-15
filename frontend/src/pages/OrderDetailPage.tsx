import { Link, useLocation, useParams } from 'react-router-dom'
import { ordersApi } from '../api/orders'
import { useAsync } from '../hooks/useAsync'
import { Loader, ErrorNote } from '../components/ui'
import { StatusBadge } from '../components/StatusBadge'
import { formatDate, formatMoney } from '../lib/format'

export function OrderDetailPage() {
  const { id } = useParams()
  const orderId = Number(id)
  const location = useLocation()
  const justPlaced = (location.state as { justPlaced?: boolean } | null)?.justPlaced ?? false

  const { data: order, loading, error } = useAsync(() => ordersApi.get(orderId), [orderId])

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
        </div>
      </div>

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
    </div>
  )
}
