import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { useWishlist } from '../../hooks/useWishlist'
import { errMessage } from '../../lib/errors'
import { ErrorNote } from '../ui'
import { cx } from '../../lib/format'

/** Save / unsave a product. Signed-out visitors are sent to log in first. */
export function WishlistButton({ productId }: { productId: number }) {
  const { isAuthenticated } = useAuth()
  const { isSaved, add, remove } = useWishlist()
  const navigate = useNavigate()
  const location = useLocation()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const saved = isAuthenticated && isSaved(productId)

  async function toggle() {
    if (!isAuthenticated) {
      navigate('/login', { state: { from: location.pathname } })
      return
    }
    setBusy(true)
    setError(null)
    try {
      if (saved) await remove(productId)
      else await add(productId)
    } catch (err) {
      setError(errMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <button
        type="button"
        className={cx('btn btn--ghost wish-btn', saved && 'is-saved')}
        aria-pressed={saved}
        onClick={toggle}
        disabled={busy}
      >
        <span aria-hidden="true">{saved ? '♥' : '♡'}</span>
        {saved ? 'Saved to wishlist' : 'Save to wishlist'}
      </button>
      {error && <ErrorNote message={error} />}
    </>
  )
}
