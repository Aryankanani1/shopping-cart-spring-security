// Load test: shoppers browsing, checking out and paying, while the API's database
// connection pool is sampled. Run it with k6 against a running API (see the README,
// "Load testing"), for example:
//
//   docker run --rm -v "$PWD/load:/load:ro" grafana/k6:2.3.0 run /load/checkout.js \
//     -e BASE_URL=http://host.docker.internal:8082
//
// Each virtual user signs up as its own customer, then loops: browse products, add
// one to the cart, check out, pay (with an Idempotency-Key), view the paid order.
// A second scenario reads the pool's active and pending connections from the
// actuator once a second, as an admin, so the summary shows the pool next to the
// latencies: with the provider call outside any transaction, a slow provider
// (APP_PAYMENT_FAKE_LATENCY) should add latency to "pay" but leave the pool alone.

import http from 'k6/http'
import { check, fail, sleep } from 'k6'
import { Trend } from 'k6/metrics'
import exec from 'k6/execution'

const API = `${__ENV.BASE_URL || 'http://localhost:8082'}/api/v1`
const ADMIN = {
  email: __ENV.ADMIN_EMAIL || 'admin1@gmail.com',  // the dev profile's seeded admin
  password: __ENV.ADMIN_PASSWORD || '123456',
}
const PRODUCTS = 20  // shoppers spread over these, so checkouts don't all lock one product row
const ADDRESS = JSON.stringify({
  recipientName: 'Load Tester', addressLine1: '1 Load Street', addressLine2: '',
  city: 'Testville', state: 'TS', postalCode: '12345', country: 'US',
})

const poolActive = new Trend('db_pool_active')
const poolPending = new Trend('db_pool_pending')

export const options = {
  scenarios: {
    shoppers: {
      executor: 'ramping-vus',
      exec: 'shop',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '30s', target: 50 },
        { duration: '1m', target: 50 },
        { duration: '20s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
    pool: {
      executor: 'constant-arrival-rate',
      exec: 'samplePool',
      rate: 1,
      timeUnit: '1s',
      duration: '2m20s',
      preAllocatedVUs: 1,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    'http_req_duration{step:browse}': ['p(95)<300'],
    'http_req_duration{step:checkout}': ['p(95)<800'],
    'http_req_duration{step:pay}': ['p(95)<1500'],
    // No threshold on the pool: at 50 shoppers its 10 connections are the busiest
    // resource, and requests briefly waiting for one is Hikari's normal back-pressure.
    // Waits long enough to matter show up in the latencies above; the pool's numbers
    // are in the summary (db_pool_active, db_pool_pending) to compare runs.
  },
}

function login(email, password) {
  const res = http.post(`${API}/auth/login`, JSON.stringify({ email, password }),
    { headers: { 'Content-Type': 'application/json' }, tags: { step: 'login' } })
  if (res.status !== 200) fail(`login as ${email} failed: ${res.status} ${res.body}`)
  return res.json('data.token')
}

function bearer(token, extra = {}) {
  return { Authorization: `Bearer ${token}`, ...extra }
}

// A failed check says why: the status (0 is a network error, not the API) and body.
function report(res, ok, step) {
  if (!ok) console.warn(`${step} failed: status ${res.status} ${res.error || ''} ${String(res.body).slice(0, 200)}`)
  return ok
}

function uuid() {
  return crypto.randomUUID()
}

// Once, before the load: products with stock that won't run out, as the admin.
export function setup() {
  const token = login(ADMIN.email, ADMIN.password)
  const run = Date.now().toString(36)
  const productIds = []
  for (let i = 0; i < PRODUCTS; i++) {
    const res = http.post(`${API}/products`, JSON.stringify({
      name: `Load ${run} ${i}`, brand: 'k6', price: 10, description: 'Created by the load test.',
      inventory: 1000000, category: { name: `Load ${run}` },
    }), { headers: bearer(token, { 'Content-Type': 'application/json' }) })
    if (res.status !== 201) fail(`creating a product failed: ${res.status} ${res.body}`)
    productIds.push(res.json('data.id'))
  }
  return { productIds, adminToken: token }
}

// Each virtual user's own customer, signed up on its first iteration.
let token = null

export function shop(data) {
  if (token === null) {
    const email = `load-${exec.vu.idInTest}-${Date.now().toString(36)}@load.test`
    const password = `pw-${uuid()}`
    const signUp = http.post(`${API}/users`, JSON.stringify({
      firstName: 'Load', lastName: `Tester ${exec.vu.idInTest}`, email, password,
    }), { headers: { 'Content-Type': 'application/json' }, tags: { step: 'sign-up' } })
    if (signUp.status !== 201) fail(`sign-up failed: ${signUp.status} ${signUp.body}`)
    token = login(email, password)
  }
  const productId = data.productIds[exec.vu.idInTest % data.productIds.length]

  const browse = http.get(`${API}/products?page=0&size=20`, { tags: { step: 'browse' } })
  check(browse, { 'browse: 200': (r) => r.status === 200 })

  const add = http.post(`${API}/cartItems?productId=${productId}&quantity=1`, null,
    { headers: bearer(token), tags: { step: 'add-to-cart' } })
  check(add, { 'add to cart: 201': (r) => r.status === 201 })

  const order = http.post(`${API}/orders`, ADDRESS,
    { headers: bearer(token, { 'Content-Type': 'application/json' }), tags: { step: 'checkout' } })
  if (!report(order, check(order, { 'checkout: 201': (r) => r.status === 201 }), 'checkout')) return
  const orderId = order.json('data.id')

  const pay = http.post(`${API}/orders/${orderId}/payments`, JSON.stringify({ paymentMethod: 'test_visa' }),
    { headers: bearer(token, { 'Content-Type': 'application/json', 'Idempotency-Key': uuid() }), tags: { step: 'pay' } })
  report(pay, check(pay, { 'pay: 201 SUCCEEDED': (r) => r.status === 201 && r.json('data.status') === 'SUCCEEDED' }), 'pay')

  const view = http.get(`${API}/orders/${orderId}`, { headers: bearer(token), tags: { step: 'view-order' } })
  check(view, { 'order: PAID': (r) => r.status === 200 && r.json('data.status') === 'PAID' })

  sleep(1)  // think time between purchases
}

// Once a second: the pool's connections in use, and requests waiting for one.
export function samplePool(data) {
  for (const [metric, trend] of [['active', poolActive], ['pending', poolPending]]) {
    const res = http.get(`${API.replace('/api/v1', '')}/actuator/metrics/hikaricp.connections.${metric}`,
      { headers: bearer(data.adminToken), tags: { step: 'pool-sample' } })
    if (res.status === 200) trend.add(res.json('measurements.0.value'))
  }
}
