import { describe, it, expect } from 'vitest'
import { recheckInterval } from './PaymentHistory'
import { payment } from '../../test/fixtures'

describe('recheckInterval', () => {
  it('checks back while a payment or refund is still settling', () => {
    expect(recheckInterval([payment({ status: 'FAILED' }), payment({ status: 'REFUND_PENDING' })])).toBe(2000)
    expect(recheckInterval([payment({ status: 'PROCESSING' })])).toBe(2000)
  })

  it('stops once everything is settled, or before anything has loaded', () => {
    expect(recheckInterval([payment({ status: 'REFUNDED' }), payment({ status: 'FAILED' })])).toBe(false)
    expect(recheckInterval([payment({ status: 'SUCCEEDED' })])).toBe(false)
    expect(recheckInterval([])).toBe(false)
    expect(recheckInterval(undefined)).toBe(false)
  })
})
