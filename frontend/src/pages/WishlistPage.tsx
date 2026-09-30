import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { imageUrl } from '../api/client'
import type { WishlistItemDto } from '../api/types'
import { useWishlist } from '../hooks/useWishlist'
import { useCart } from '../context/CartContext'
import { Loader, ErrorNote, EmptyState } from '../components/ui'
import { errMessage } from '../lib/errors'
import { formatDateTime, formatMoney, toDateTimeLocal } from '../lib/format'

export function WishlistPage() {
  const { items, isLoading, error } = useWishlist()

  return (
    <div className="container section">
      <div className="page-head">
        <span className="eyebrow">Saved for later</span>
        <h1>Wishlist</h1>
      </div>

      {error && <ErrorNote message={errMessage(error)} />}

      {isLoading ? (
        <Loader label="Loading your wishlist" />
      ) : items.length === 0 ? (
        <EmptyState title="Your wishlist is empty.">
          <p className="muted">Save things you’re not ready to buy, and we’ll remind you about them.</p>
          <Link to="/products" className="btn btn--accent">
            Browse the shop
          </Link>
        </EmptyState>
      ) : (
        <>
          <p className="muted wishlist__intro">
            Set a reminder on anything you’re not ready to buy yet. With alerts on, you’ll also hear
            when a price drops or a sold-out item is back. Everything arrives under{' '}
            <Link to="/notifications" className="link">
              Alerts
            </Link>
            .
          </p>
          <ul className="wishlist">
            {items.map((item) => (
              <WishlistRow key={item.product.id} item={item} />
            ))}
          </ul>
        </>
      )}
    </div>
  )
}

function tomorrowAtNine(): Date {
  const d = new Date()
  d.setDate(d.getDate() + 1)
  d.setHours(9, 0, 0, 0)
  return d
}

function WishlistRow({ item }: { item: WishlistItemDto }) {
  const { remove, setReminder, clearReminder, setAlerts } = useWishlist()
  const { addItem } = useCart()
  const [editing, setEditing] = useState(false)
  const [when, setWhen] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [added, setAdded] = useState(false)

  const { product } = item
  const soldOut = product.inventory <= 0
  const dropped = product.price < item.priceWhenAdded
  const img = product.images?.[0]

  // Run one row action with shared busy/error handling; resolves to success.
  async function run(action: () => Promise<unknown>): Promise<boolean> {
    setBusy(true)
    setError(null)
    try {
      await action()
      return true
    } catch (err) {
      setError(errMessage(err))
      return false
    } finally {
      setBusy(false)
    }
  }

  function openEditor() {
    setWhen(toDateTimeLocal(item.remindAt ? new Date(item.remindAt) : tomorrowAtNine()))
    setError(null)
    setEditing(true)
  }

  async function saveReminder(e: FormEvent) {
    e.preventDefault()
    // datetime-local is the user's local time; send it as an absolute instant.
    const at = new Date(when)
    if (Number.isNaN(at.getTime()) || at.getTime() <= Date.now()) {
      setError('Pick a time in the future.')
      return
    }
    if (await run(() => setReminder({ productId: product.id, remindAt: at.toISOString() }))) {
      setEditing(false)
    }
  }

  async function addToBag() {
    setAdded(false)
    if (await run(() => addItem(product.id, 1))) setAdded(true)
  }

  return (
    <li className="wish-line" aria-busy={busy || undefined}>
      <Link to={`/products/${product.id}`} className="wish-line__media">
        {img ? (
          <img src={imageUrl(img.imageId)} alt={product.name} />
        ) : (
          <span className="serif">{product.name.charAt(0)}</span>
        )}
      </Link>

      <div className="wish-line__info">
        <span className="eyebrow">{product.brand}</span>
        <Link to={`/products/${product.id}`} className="wish-line__name serif link">
          {product.name}
        </Link>
        <div className="wish-line__price">
          <span className="price">{formatMoney(product.price)}</span>
          {dropped && (
            <>
              <s className="faint">{formatMoney(item.priceWhenAdded)}</s>
              <span className="tag tag--ok">
                <span className="dot" />
                Price dropped
              </span>
            </>
          )}
          {soldOut && (
            <span className="tag tag--danger">
              <span className="dot" />
              Sold out
            </span>
          )}
        </div>
      </div>

      <div className="wish-line__actions">
        <button className="btn btn--accent btn--sm" onClick={addToBag} disabled={busy || soldOut}>
          {soldOut ? 'Sold out' : 'Add to bag'}
        </button>
        <button className="linkbtn" onClick={() => run(() => remove(product.id))} disabled={busy}>
          Remove
        </button>
      </div>

      <div className="wish-line__settings">
        {editing ? (
          <form className="wish-line__reminder" onSubmit={saveReminder}>
            <label className="label" htmlFor={`remind-${product.id}`}>
              Remind me on
            </label>
            <input
              id={`remind-${product.id}`}
              className="field"
              type="datetime-local"
              value={when}
              min={toDateTimeLocal(new Date())}
              onChange={(e) => setWhen(e.target.value)}
              required
            />
            <button className="btn btn--accent btn--sm" type="submit" disabled={busy}>
              Save reminder
            </button>
            <button className="linkbtn" type="button" onClick={() => setEditing(false)} disabled={busy}>
              Cancel
            </button>
          </form>
        ) : item.remindAt ? (
          <p className="wish-line__reminder">
            <span>Reminder: {formatDateTime(item.remindAt)}</span>
            <button className="linkbtn" onClick={openEditor} disabled={busy}>
              Change
            </button>
            <button className="linkbtn" onClick={() => run(() => clearReminder(product.id))} disabled={busy}>
              Clear
            </button>
          </p>
        ) : (
          <button className="linkbtn" onClick={openEditor} disabled={busy}>
            Set a reminder
          </button>
        )}

        <label className="wish-line__alerts">
          <input
            type="checkbox"
            checked={item.alertsEnabled}
            onChange={(e) => run(() => setAlerts({ productId: product.id, enabled: e.target.checked }))}
            disabled={busy}
          />
          Tell me about price drops and restocks
        </label>
      </div>

      {error && <ErrorNote message={error} />}
      {added && (
        <p className="note note--ok" role="status">
          Added to your bag.{' '}
          <Link to="/cart" className="link">
            View bag →
          </Link>
        </p>
      )}
    </li>
  )
}
