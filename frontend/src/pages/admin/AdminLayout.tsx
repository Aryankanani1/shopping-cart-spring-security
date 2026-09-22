import { NavLink, Outlet } from 'react-router-dom'
import { cx } from '../../lib/format'

/**
 * Shell for the /admin section: a heading, a sub-nav across the three management
 * areas, and an <Outlet> for the active page. Mounted behind <RequireAdmin>, so
 * everything rendered here is admin-only.
 */
export function AdminLayout() {
  const tab = ({ isActive }: { isActive: boolean }) => cx('admin-tab', isActive && 'is-active')

  return (
    <div className="container section">
      <span className="eyebrow">Admin</span>
      <h1 className="admin-title">Store management</h1>

      <nav className="admin-tabs">
        <NavLink to="/admin/orders" className={tab}>
          Orders
        </NavLink>
        <NavLink to="/admin/products" className={tab}>
          Products
        </NavLink>
        <NavLink to="/admin/categories" className={tab}>
          Categories
        </NavLink>
      </nav>

      <Outlet />
    </div>
  )
}
