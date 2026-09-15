import { Link } from 'react-router-dom'
import { vars } from '../lib/format'

export function NotFoundPage() {
  return (
    <div className="container section center stack" style={vars({ '--gap': '1.25rem' })}>
      <span className="eyebrow">Error 404</span>
      <h1 style={{ fontSize: 'clamp(3rem, 12vw, 8rem)' }}>Off the map</h1>
      <p className="muted">The page you were looking for doesn’t exist here.</p>
      <div>
        <Link to="/" className="btn btn--ghost">
          Back to the store
        </Link>
      </div>
    </div>
  )
}
