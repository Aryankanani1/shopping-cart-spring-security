import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { productsApi } from '../api/products'
import { categoriesApi } from '../api/categories'
import { queryKeys } from '../api/queryKeys'
import { errMessage } from '../lib/errors'
import { ProductCard } from '../components/ProductCard'
import { Loader, ErrorNote } from '../components/ui'

const FEATURED = { size: 8, sort: 'id,desc' }

export function HomePage() {
  const productsQuery = useQuery({
    queryKey: queryKeys.products.list(FEATURED),
    queryFn: () => productsApi.list(FEATURED),
  })
  // Shared key with the Shop page — resolves to one cached request, not two.
  const categoriesQuery = useQuery({
    queryKey: queryKeys.categories,
    queryFn: () => categoriesApi.list(),
  })

  const products = productsQuery.data?.content ?? []
  const categories = categoriesQuery.data ?? []
  const loading = productsQuery.isLoading
  const error = productsQuery.error ? errMessage(productsQuery.error) : null

  return (
    <>
      <section className="hero">
        <div className="container hero__inner">
          <div className="hero__copy rise">
            <span className="eyebrow">Meridian · General Store</span>
            <h1 className="hero__title">
              Everyday objects,
              <br /> chosen with care.
            </h1>
            <p className="hero__lead muted">
              A compact catalogue of well-made things — tools, wares and small luxuries for the
              daily round.
            </p>
            <div className="hero__cta">
              <Link to="/products" className="btn btn--accent">
                Browse the catalogue
              </Link>
              <Link to="/register" className="link link--accent">
                Create an account →
              </Link>
            </div>
          </div>
          <div className="hero__mark rise" style={{ animationDelay: '120ms' }} aria-hidden="true">
            <span className="hero__glyph serif">✳</span>
            <span className="hero__mark-label eyebrow">No. 01 — Edition</span>
          </div>
        </div>
      </section>

      <section className="section container">
        <div className="section__head">
          <h2>Selected editions</h2>
          <Link to="/products" className="link">
            View all
          </Link>
        </div>

        {loading && <Loader label="Loading catalogue" />}
        {error && <ErrorNote message={error} />}
        {!loading && !error && (
          <div className="product-grid">
            {products.map((product, i) => (
              <ProductCard key={product.id} product={product} index={i} />
            ))}
          </div>
        )}
      </section>

      {categories.length > 0 && (
        <section className="section container">
          <div className="section__head">
            <h2>Shop by category</h2>
          </div>
          <div className="chips">
            {categories.map((c) => (
              <Link key={c.id} to={`/products?category=${encodeURIComponent(c.name)}`} className="chip">
                {c.name}
              </Link>
            ))}
          </div>
        </section>
      )}
    </>
  )
}
