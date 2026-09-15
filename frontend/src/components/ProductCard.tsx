import { Link } from 'react-router-dom'
import { imageUrl } from '../api/client'
import type { ProductDto } from '../api/types'
import { formatMoney } from '../lib/format'

export function ProductCard({ product, index = 0 }: { product: ProductDto; index?: number }) {
  const image = product.images?.[0]
  const soldOut = product.inventory <= 0

  return (
    <Link
      to={`/products/${product.id}`}
      className="product-card rise"
      style={{ animationDelay: `${Math.min(index, 12) * 55}ms` }}
    >
      <div className="product-card__media">
        {image ? (
          <img
            className="product-card__img"
            src={imageUrl(image.imageId)}
            alt={product.name}
            loading="lazy"
          />
        ) : (
          <span className="product-card__ph serif">{product.name.charAt(0)}</span>
        )}
        {soldOut && <span className="product-card__flag">Sold out</span>}
      </div>

      <div className="product-card__body">
        <span className="eyebrow">{product.brand || 'Meridian'}</span>
        <span className="product-card__name serif">{product.name}</span>
        <div className="product-card__foot">
          <span className="price">{formatMoney(product.price)}</span>
          {product.categoryName && <span className="faint product-card__cat">{product.categoryName}</span>}
        </div>
      </div>
    </Link>
  )
}
