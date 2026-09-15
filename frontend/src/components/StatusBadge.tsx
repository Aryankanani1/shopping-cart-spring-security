import { cx } from '../lib/format'

// Map each OrderStatus to a tone. PENDING/PROCESSING stay neutral.
const VARIANT: Record<string, string> = {
  SHIPPED: 'tag--ok',
  DELIVERED: 'tag--ok',
  CANCELLED: 'tag--danger',
}

export function StatusBadge({ status }: { status: string }) {
  return (
    <span className={cx('tag', VARIANT[status])}>
      <span className="dot" />
      {status.toLowerCase()}
    </span>
  )
}
