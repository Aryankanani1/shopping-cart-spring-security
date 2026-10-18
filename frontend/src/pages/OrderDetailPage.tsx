import { useEffect, useRef } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ordersApi } from '../api/orders'
import { paymentsApi } from '../api/payments'
import { queryKeys } from '../api/queryKeys'
import { refundsApi } from '../api/refunds'
import { useAuth } from '../context/AuthContext'
import { errMessage } from '../lib/errors'
import { Loader, ErrorNote } from '../components/ui'
import { StatusBadge } from '../components/ui/StatusBadge'
import { PaymentPanel } from '../components/order/PaymentPanel'
import { PaymentHistory, recheckInterval } from '../components/order/PaymentHistory'
import { RefundList, refundRecheckInterval } from '../components/order/RefundList'
import { formatDate, formatMoney } from '../lib/format'

// Statuses from which the owner may still cancel — mirrors the server's rule
// (until the order ships; a paid one is refunded).
const CANCELLABLE = new Set(['PENDING', 'PAID', 'PROCESSING'])

export function OrderDetailPage() {
  const { id } = useParams()
  const orderId = Number(id)
  const location = useLocation()
  const justPlaced = (location.state as { justPlaced?: boolean } | null)?.justPlaced ?? false
  const queryClient = useQueryClient()
  const { userId } = useAuth()

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

  const { data: payments } = useQuery({
    queryKey: queryKeys.orders.payments(orderId),
    queryFn: () => paymentsApi.list(orderId),
    enabled: Number.isFinite(orderId),
    refetchInterval: (query) => recheckInterval(query.state.data),
  })
  // A payment the provider didn't answer in time is settled in the background
  // (reconciliation). Until then it can't be paid again or cancelled; once it is
  // settled, the order may have become paid, so fetch it again.
  const confirming = payments?.some((payment) => payment.status === 'PROCESSING') ?? false
  const wasConfirming = useRef(false)
  useEffect(() => {
    if (wasConfirming.current && !confirming) {
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.detail(orderId) })
    }
    wasConfirming.current = confirming
  }, [confirming, orderId, queryClient])

  const { data: refunds } = useQuery({
    queryKey: queryKeys.orders.refunds(orderId),
    queryFn: () => refundsApi.list(orderId),
    enabled: Number.isFinite(orderId),
    refetchInterval: (query) => refundRecheckInterval(query.state.data),
  })
  // The provider makes a refund after it is committed. Once the last pending one is
  // made, the payment says how much has gone back (and may be refunded in full), so
  // fetch it again.
  const refunding = refunds?.some((refund) => refund.status === 'PENDING') ?? false
  const wasRefunding = useRef(false)
  useEffect(() => {
    if (wasRefunding.current && !refunding) {
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.payments(orderId) })
    }
    wasRefunding.current = refunding
  }, [refunding, orderId, queryClient])

  const cancel = useMutation({
    mutationFn: () => ordersApi.cancel(orderId),
    onSuccess: (updated) => {
      // Push the returned order into the detail cache and refresh any history list.
      queryClient.setQueryData(queryKeys.orders.detail(orderId), updated)
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.all })
      // Cancelling puts the items back in stock.
      queryClient.invalidateQueries({ queryKey: queryKeys.products.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.wishlist(userId) })
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
          {CANCELLABLE.has(order.status) && !confirming && (
            <button
              className="linkbtn"
              onClick={() => {
                const refund = order.status === 'PENDING' ? '' : ' and your payment refunded'
                if (window.confirm(`Cancel this order? Items will be returned to stock${refund}.`)) {
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

      {order.status === 'PENDING' && (
        <PaymentPanel orderId={orderId} amount={order.totalAmount} payBy={order.payBy} confirming={confirming} />
      )}

      <ul className="order-items">
        <li className="order-item order-item--head">
          <span className="eyebrow">Item</span>
          <span className="eyebrow">Qty</span>
          <span className="eyebrow">Unit</span>
          <span className="eyebrow order-item__right">Total</span>
        </li>
        {(order.items ?? []).map((item) => (
          <li className="order-item" key={item.id}>
            <div className="order-item__info">
              <span className="order-item__name serif">{item.productName}</span>
              {item.productBrand && <span className="faint">{item.productBrand}</span>}
              {item.refundedQuantity > 0 && <span className="faint">{item.refundedQuantity} refunded</span>}
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

      <PaymentHistory payments={payments} />
      <RefundList refunds={refunds} items={order.items ?? []} />
    </div>
  )
}
