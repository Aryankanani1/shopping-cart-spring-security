import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { imageUrl } from '../api/client'
import { ordersApi } from '../api/orders'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { QuantityStepper } from '../components/QuantityStepper'
import { Loader, ErrorNote, EmptyState } from '../components/ui'
import { errMessage } from '../lib/errors'
import { formatMoney } from '../lib/format'

export function CartPage() {
  const { userId } = useAuth()
  const { cart, loading, refresh, setQuantity, removeItem, clear } = useCart()
  const navigate = useNavigate()

  const [busyItem, setBusyItem] = useState<number | null>(null)
  const [placing, setPlacing] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const items = cart?.cartItems ?? []

  async function changeQty(itemId: number, qty: number) {
    setBusyItem(itemId)
    setError(null)
    try {
      await setQuantity(itemId, qty)
    } catch (err) {
      setError(errMessage(err))
    } finally {
      setBusyItem(null)
    }
  }

  async function remove(productId: number, itemId: number) {
    setBusyItem(itemId)
    setError(null)
    try {
      await removeItem(productId)
    } catch (err) {
      setError(errMessage(err))
    } finally {
      setBusyItem(null)
    }
  }

  async function emptyBag() {
    setError(null)
    try {
      await clear()
    } catch (err) {
      setError(errMessage(err))
    }
  }

  async function checkout() {
    if (!userId) return
    setPlacing(true)
    setError(null)
    try {
      const order = await ordersApi.place(userId)
      await refresh()
      navigate(`/orders/${order.id}`, { state: { justPlaced: true } })
    } catch (err) {
      setError(errMessage(err))
      setPlacing(false)
    }
  }

  if (loading && !cart) {
    return (
      <div className="container section">
        <Loader label="Loading your bag" />
      </div>
    )
  }

  return (
    <div className="container section">
      <div className="page-head">
        <span className="eyebrow">Your bag</span>
        <h1>Bag</h1>
      </div>

      {error && <ErrorNote message={error} />}

      {items.length === 0 ? (
        <EmptyState title="Your bag is empty.">
          <Link to="/products" className="btn btn--accent">
            Start shopping
          </Link>
        </EmptyState>
      ) : (
        <div className="cart">
          <ul className="cart__list">
            {items.map((item) => {
              const img = item.product.images?.[0]
              const busy = busyItem === item.itemId
              return (
                <li className="cart-line" key={item.itemId} aria-busy={busy || undefined}>
                  <Link to={`/products/${item.product.id}`} className="cart-line__media">
                    {img ? (
                      <img src={imageUrl(img.imageId)} alt={item.product.name} />
                    ) : (
                      <span className="serif">{item.product.name.charAt(0)}</span>
                    )}
                  </Link>

                  <div className="cart-line__info">
                    <span className="eyebrow">{item.product.brand}</span>
                    <Link to={`/products/${item.product.id}`} className="cart-line__name serif link">
                      {item.product.name}
                    </Link>
                    <span className="faint">{formatMoney(item.unitPrice)} each</span>
                  </div>

                  <div className="cart-line__qty">
                    <QuantityStepper
                      value={item.quantity}
                      busy={busy}
                      onChange={(v) => changeQty(item.itemId, v)}
                    />
                    <button
                      className="linkbtn"
                      onClick={() => remove(item.product.id, item.itemId)}
                      disabled={busy}
                    >
                      Remove
                    </button>
                  </div>

                  <div className="cart-line__price price">
                    {formatMoney(item.unitPrice * item.quantity)}
                  </div>
                </li>
              )
            })}
          </ul>

          <aside className="cart-summary card">
            <h3 className="cart-summary__title serif">Summary</h3>
            <div className="cart-summary__row">
              <span className="muted">Subtotal</span>
              <span className="price">{formatMoney(cart?.totalAmount)}</span>
            </div>
            <div className="cart-summary__row faint">
              <span>Shipping</span>
              <span>Calculated at checkout</span>
            </div>
            <hr className="hairline" />
            <div className="cart-summary__row cart-summary__total">
              <span>Total</span>
              <span className="price">{formatMoney(cart?.totalAmount)}</span>
            </div>
            <button className="btn btn--accent btn--block" onClick={checkout} disabled={placing}>
              {placing ? 'Placing order…' : 'Checkout'}
            </button>
            <button className="linkbtn cart-summary__clear" onClick={emptyBag} disabled={placing}>
              Empty bag
            </button>
          </aside>
        </div>
      )}
    </div>
  )
}
