import type { ReactNode } from 'react'
import { cx } from '../lib/format'

export function Spinner({ large = false }: { large?: boolean }) {
  return <span className={cx('spinner', large && 'spinner--lg')} aria-hidden="true" />
}

export function Loader({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="loader" role="status">
      <Spinner large />
      <span className="eyebrow">{label}</span>
    </div>
  )
}

export function ErrorNote({ message }: { message: string }) {
  return (
    <p className="note note--error" role="alert">
      {message}
    </p>
  )
}

export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="empty">
      <p className="empty__title serif">{title}</p>
      {children && <div className="empty__body">{children}</div>}
    </div>
  )
}
