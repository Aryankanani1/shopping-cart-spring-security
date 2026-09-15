import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { productsApi } from '../api/products'
import { imageUrl } from '../api/client'
import { useAsync, errMessage } from '../hooks/useAsync'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { QuantityStepper } from '../components/QuantityStepper'
import { Loader, ErrorNote } from '../components/ui'
import { cx, formatMoney, vars } from '../lib/format'

export function ProductDetailPage() {
  const { id } = useParams()
  const productId = Number(id)
  const { isAuthenticated } = useAuth()
  const { addItem } = useCart()
  const navigate = useNavigate()
  const location = useLocation()

  const { data: product, loading, error } = useAsync(() => productsApi.get(productId), [productId])

  const [selected, setSelected] = useState(0)
  const [qty, setQty] = useState(1)
  const [adding, setAdding] = useState(false)
  const [added, setAdded] = useState(false)
  const [addError, setAddError] = useState<string | null>(null)

  if (loading) {
    return (
      <div className="container section">
        <Loader label="Loading product" />
      </div>
    )
  }
  if (error || !product) {
    return (
      <div className="container section">
        <ErrorNote message={error ?? 'Product not found.'} />
        <p style={{ marginTop: '1rem' }}>
          <Link to="/products" className="link link--accent">
            ← Back to shop
          </Link>
        </p>
      </div>
    )
  }

  const images = product.images ?? []
  const activeImage = images[selected]
  const soldOut = product.inventory <= 0
  const lowStock = !soldOut && product.inventory <= 5
  const maxQty = Math.max(1, Math.min(product.inventory || 1, 99))

  async function handleAdd() {
    if (!product) return
    if (!isAuthenticated) {
      navigate('/login', { state: { from: location.pathname } })
      return
    }
    setAdding(true)
    setAddError(null)
    try {
      await addItem(product.id, qty)
      setAdded(true)
    } catch (err) {
      setAddError(errMessage(err))
    } finally {
      setAdding(false)
    }
  }

  return (
    <div className="container section pdp">
      <Link to="/products" className="link pdp__back">
        ← Shop
      </Link>

      <div className="pdp__grid">
        <div className="pdp__gallery">
          <div className="pdp__stage">
            {activeImage ? (
              <img src={imageUrl(activeImage.imageId)} alt={product.name} className="pdp__img" />
            ) : (
              <span className="pdp__ph serif">{product.name.charAt(0)}</span>
            )}
          </div>
          {images.length > 1 && (
            <div className="pdp__thumbs">
              {images.map((img, i) => (
                <button
                  key={img.imageId}
                  className={cx('pdp__thumb', i === selected && 'is-active')}
                  onClick={() => setSelected(i)}
                  aria-label={`View image ${i + 1}`}
                >
                  <img src={imageUrl(img.imageId)} alt="" />
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="pdp__info stack" style={vars({ '--gap': '1.25rem' })}>
          <div>
            <span className="eyebrow">{product.brand || 'Meridian'}</span>
            <h1 className="pdp__title">{product.name}</h1>
          </div>

          <div className="pdp__pricerow">
            <span className="price pdp__price">{formatMoney(product.price)}</span>
            {soldOut ? (
              <span className="tag tag--danger">
                <span className="dot" />
                Sold out
              </span>
            ) : lowStock ? (
              <span className="tag tag--danger">
                <span className="dot" />
                Only {product.inventory} left
              </span>
            ) : (
              <span className="tag tag--ok">
                <span className="dot" />
                In stock
              </span>
            )}
          </div>

          {product.description && <p className="pdp__desc muted">{product.description}</p>}

          {product.categoryName && (
            <p className="faint">
              Category:{' '}
              <Link
                to={`/products?category=${encodeURIComponent(product.categoryName)}`}
                className="link"
              >
                {product.categoryName}
              </Link>
            </p>
          )}

          <hr className="hairline" />

          {addError && <ErrorNote message={addError} />}
          {added && (
            <p className="note note--ok" role="status">
              Added to your bag.{' '}
              <Link to="/cart" className="link">
                View bag →
              </Link>
            </p>
          )}

          <div className="pdp__buy">
            <QuantityStepper
              value={qty}
              max={maxQty}
              onChange={(v) => {
                setQty(v)
                setAdded(false)
              }}
              disabled={soldOut}
            />
            <button
              className="btn btn--accent grow"
              onClick={handleAdd}
              disabled={soldOut || adding}
            >
              {soldOut ? 'Sold out' : adding ? 'Adding…' : 'Add to bag'}
            </button>
          </div>

          {!isAuthenticated && (
            <p className="faint pdp__signin">
              You’ll be asked to{' '}
              <Link to="/login" className="link">
                sign in
              </Link>{' '}
              to add items.
            </p>
          )}
        </div>
      </div>
    </div>
  )
}
