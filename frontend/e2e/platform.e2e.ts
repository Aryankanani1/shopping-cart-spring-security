import { expect, test } from '@playwright/test'

// What the deployment adds around the application: HTTPS, Caddy's headers and
// routing, and the API behind /api with the actuator kept internal.

test('serves the storefront over HTTPS with the security headers', async ({ request }) => {
  const res = await request.get('/')
  expect(res.status()).toBe(200)
  const headers = res.headers()
  expect(headers['strict-transport-security']).toBe('max-age=31536000')
  expect(headers['x-content-type-options']).toBe('nosniff')
  expect(headers['referrer-policy']).toBe('strict-origin-when-cross-origin')
  expect(headers['cache-control']).toBe('no-cache')
  expect(headers['server']).toBeUndefined()
  expect(await res.text()).toContain('<div id="root">')
})

test('answers a client-side route with index.html', async ({ request }) => {
  const res = await request.get('/orders/42')
  expect(res.status()).toBe(200)
  expect(await res.text()).toContain('<div id="root">')
})

test('lets browsers cache the hashed assets for good', async ({ request }) => {
  const html = await (await request.get('/')).text()
  const asset = html.match(/\/assets\/[^"]+\.js/)?.[0]
  expect(asset).toBeDefined()
  const res = await request.get(asset!)
  expect(res.status()).toBe(200)
  expect(res.headers()['cache-control']).toBe('public, max-age=31536000, immutable')
})

test('redirects plain HTTP to HTTPS', async ({ request, baseURL }) => {
  const http = new URL(baseURL!)
  http.protocol = 'http:'
  http.port = ''
  const res = await request.get(http.toString(), { maxRedirects: 0 })
  expect(res.status()).toBe(308)
  expect(res.headers()['location']).toMatch(/^https:\/\//)
})

test('keeps the actuator internal', async ({ request }) => {
  // Not proxied: the storefront answers it like any other unknown path.
  const direct = await request.get('/actuator/health')
  expect(await direct.text()).not.toContain('"status"')
  // And the API has nothing under /api/actuator.
  const viaApi = await request.get('/api/actuator/health')
  expect(viaApi.status()).not.toBe(200)
})

test('serves the API under /api and keeps private data behind sign-in', async ({ request }) => {
  const products = await request.get('/api/v1/products')
  expect(products.status()).toBe(200)
  expect(await products.json()).toHaveProperty('data')

  const orders = await request.get('/api/v1/orders')
  expect(orders.status()).toBe(401)
  expect(orders.headers()['content-type']).toContain('application/problem+json')
})
