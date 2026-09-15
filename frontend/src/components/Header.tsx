import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { cx } from '../lib/format'

export function Header() {
  const { isAuthenticated, logout } = useAuth()
  const { itemCount } = useCart()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate('/')
  }

  const navLink = ({ isActive }: { isActive: boolean }) => cx('nav__link', isActive && 'is-active')

  return (
    <>
      <div className="announce">
        <span>Free shipping over $75</span>
        <span className="announce__sep">✦</span>
        <span>New editions every Thursday</span>
      </div>

      <header className="masthead">
        <div className="container masthead__inner">
          <Link to="/" className="wordmark" aria-label="Meridian, home">
            Meridian<span className="wordmark__tag">Gen. Store</span>
          </Link>

          <nav className="nav">
            <NavLink to="/products" className={navLink}>
              Shop
            </NavLink>
            {isAuthenticated && (
              <NavLink to="/orders" className={navLink}>
                Orders
              </NavLink>
            )}
            {isAuthenticated ? (
              <button type="button" className="nav__link linkbtn" onClick={handleLogout}>
                Log out
              </button>
            ) : (
              <NavLink to="/login" className={navLink}>
                Sign in
              </NavLink>
            )}
            <Link to="/cart" className="cart-link" aria-label={`Bag, ${itemCount} items`}>
              Bag
              {itemCount > 0 && <span className="cart-badge">{itemCount}</span>}
            </Link>
          </nav>
        </div>
      </header>
    </>
  )
}
