import { Link } from 'react-router-dom'

export function Footer() {
  return (
    <footer className="footer">
      <div className="container footer__inner">
        <div className="footer__brand">
          <p className="wordmark wordmark--lg">Meridian</p>
          <p className="faint footer__blurb">
            A small catalogue of everyday objects, well made and fairly priced.
          </p>
        </div>

        <nav className="footer__links">
          <span className="eyebrow">Browse</span>
          <Link to="/products" className="link">
            All products
          </Link>
          <Link to="/orders" className="link">
            Your orders
          </Link>
          <Link to="/cart" className="link">
            Your bag
          </Link>
        </nav>

        <div className="footer__meta">
          <span className="eyebrow">Est. 2026</span>
          <span className="faint">A demo storefront on the Spring Security shopping-cart API.</span>
        </div>
      </div>
    </footer>
  )
}
