import type { CSSProperties } from 'react'

const money = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })

/** Format a numeric amount as USD. Currency is easy to swap here later. */
export function formatMoney(value: number | undefined | null): string {
  if (value === undefined || value === null || Number.isNaN(value)) return '—'
  return money.format(value)
}

/** Format an ISO date ("2026-09-14") as a readable local date, TZ-safe. */
export function formatDate(iso: string | undefined | null): string {
  if (!iso) return '—'
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso)
  const d = m ? new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3])) : new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' })
}

/** Join class names, dropping falsy values. */
export function cx(...parts: Array<string | false | null | undefined>): string {
  return parts.filter(Boolean).join(' ')
}

/** Build an inline style object that may include CSS custom properties (--x). */
export function vars(style: Record<string, string | number>): CSSProperties {
  return style as CSSProperties
}
