import { describe, it, expect } from 'vitest'
import { cx, formatDate, formatMoney } from './format'

describe('formatMoney', () => {
  it('formats numbers as USD', () => {
    expect(formatMoney(19.99)).toBe('$19.99')
    expect(formatMoney(0)).toBe('$0.00')
    expect(formatMoney(1234.5)).toBe('$1,234.50')
  })

  it('shows a dash for missing or invalid values', () => {
    expect(formatMoney(null)).toBe('—')
    expect(formatMoney(undefined)).toBe('—')
    expect(formatMoney(NaN)).toBe('—')
  })
})

describe('formatDate', () => {
  it('formats an ISO date without timezone drift', () => {
    expect(formatDate('2026-09-14')).toBe('September 14, 2026')
  })

  it('handles missing input', () => {
    expect(formatDate(null)).toBe('—')
    expect(formatDate(undefined)).toBe('—')
  })
})

describe('cx', () => {
  it('joins truthy class names and drops the rest', () => {
    expect(cx('a', false, undefined, 'b', null, '')).toBe('a b')
  })
})
