import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { ordersApi } from '../api/orders'
import type { ShippingAddress } from '../api/types'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { ErrorNote, Loader } from '../components/ui'
import { errMessage } from '../lib/errors'
import { formatMoney } from '../lib/format'

const EMPTY: ShippingAddress = {
  recipientName: '',
  addressLine1: '',
  addressLine2: '',
  city: '',
  state: '',
  postalCode: '',
  country: '',
}

export function CheckoutPage() {
  const { userId } = useAuth()
  const { cart, loading, refresh } = useCart()
  const navigate = useNavigate()
  const [address, setAddress] = useState<ShippingAddress>(EMPTY)

  const place = useMutation({
    mutationFn: () => ordersApi.place(userId as number, address),
    onSuccess: async (order) => {
      await refresh() // cart was cleared server-side
      navigate(`/orders/${order.id}`, { state: { justPlaced: true } })
    },
  })

  const items = cart?.cartItems ?? []

  if (loading && !cart) {
    return (
      <div className="container section">
        <Loader label="Loading checkout" />
      </div>
    )
  }

  // Nothing to check out — send them back to the (empty) bag.
  if (items.length === 0) {
    return <Navigate to="/cart" replace />
  }

  function set<K extends keyof ShippingAddress>(key: K, value: string) {
    setAddress((a) => ({ ...a, [key]: value }))
  }

  function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    place.mutate()
  }

  return (
    <div className="container section">
      <div className="page-head">
        <span className="eyebrow">Checkout</span>
        <h1>Shipping details</h1>
      </div>

      {place.isError && <ErrorNote message={errMessage(place.error)} />}

      <div className="checkout">
        <form className="checkout__form" onSubmit={onSubmit}>
          <div className="checkout__field">
            <label className="label" htmlFor="recipientName">
              Recipient name
            </label>
            <input
              id="recipientName"
              className="field"
              value={address.recipientName}
              onChange={(e) => set('recipientName', e.target.value)}
              required
            />
          </div>

          <div className="checkout__field">
            <label className="label" htmlFor="addressLine1">
              Address line 1
            </label>
            <input
              id="addressLine1"
              className="field"
              value={address.addressLine1}
              onChange={(e) => set('addressLine1', e.target.value)}
              required
            />
          </div>

          <div className="checkout__field">
            <label className="label" htmlFor="addressLine2">
              Address line 2 <span className="faint">(optional)</span>
            </label>
            <input
              id="addressLine2"
              className="field"
              value={address.addressLine2}
              onChange={(e) => set('addressLine2', e.target.value)}
            />
          </div>

          <div className="checkout__row">
            <div className="checkout__field">
              <label className="label" htmlFor="city">
                City
              </label>
              <input
                id="city"
                className="field"
                value={address.city}
                onChange={(e) => set('city', e.target.value)}
                required
              />
            </div>
            <div className="checkout__field">
              <label className="label" htmlFor="state">
                State / region
              </label>
              <input
                id="state"
                className="field"
                value={address.state}
                onChange={(e) => set('state', e.target.value)}
                required
              />
            </div>
          </div>

          <div className="checkout__row">
            <div className="checkout__field">
              <label className="label" htmlFor="postalCode">
                Postal code
              </label>
              <input
                id="postalCode"
                className="field"
                value={address.postalCode}
                onChange={(e) => set('postalCode', e.target.value)}
                required
              />
            </div>
            <div className="checkout__field">
              <label className="label" htmlFor="country">
                Country
              </label>
              <input
                id="country"
                className="field"
                value={address.country}
                onChange={(e) => set('country', e.target.value)}
                required
              />
            </div>
          </div>

          <button className="btn btn--accent btn--block" type="submit" disabled={place.isPending}>
            {place.isPending ? 'Placing order…' : 'Place order'}
          </button>
          <p className="faint checkout__note">Payment is collected on delivery.</p>
        </form>

        <aside className="cart-summary card">
          <h3 className="cart-summary__title serif">Order summary</h3>
          <ul className="checkout__items">
            {items.map((item) => (
              <li className="checkout__item" key={item.itemId}>
                <span>
                  {item.product.name} <span className="faint">× {item.quantity}</span>
                </span>
                <span className="price">{formatMoney(item.unitPrice * item.quantity)}</span>
              </li>
            ))}
          </ul>
          <hr className="hairline" />
          <div className="cart-summary__row cart-summary__total">
            <span>Total</span>
            <span className="price">{formatMoney(cart?.totalAmount)}</span>
          </div>
          <Link to="/cart" className="linkbtn cart-summary__clear">
            ← Back to bag
          </Link>
        </aside>
      </div>
    </div>
  )
}
