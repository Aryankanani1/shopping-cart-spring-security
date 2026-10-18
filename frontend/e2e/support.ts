import { test as base, expect, type APIRequestContext } from '@playwright/test'

/**
 * The tests' `test`: Playwright's, with requests to Google Fonts aborted, so a slow
 * or unreachable font CDN can't hold up a page load (the stylesheet blocks it).
 * The tests are about this shop; the pages fall back to the system fonts.
 */
export const test = base.extend<{ offlineFonts: void }>({
  offlineFonts: [
    async ({ page }, use) => {
      await page.route(/^https:\/\/fonts\.(googleapis|gstatic)\.com\//, (route) => route.abort())
      await use()
    },
    { auto: true },
  ],
})
export { expect }

// Shared setup for the E2E tests: test data is created through the API, as the
// admin, so each test drives only the screens it is about.

export const admin = {
  email: required('E2E_ADMIN_EMAIL'),
  password: required('E2E_ADMIN_PASSWORD'),
}

function required(name: string): string {
  const value = process.env[name]
  if (!value) throw new Error(`Set ${name} (see playwright.config.ts)`)
  return value
}

/** A name no earlier run has used, so tests never depend on the database being empty. */
export function unique(prefix: string): string {
  return `${prefix} ${Date.now().toString(36)}${Math.floor(Math.random() * 1e4)}`
}

export async function adminToken(request: APIRequestContext): Promise<string> {
  const res = await request.post('/api/v1/auth/login', { data: admin })
  expect(res.status(), await res.text()).toBe(200)
  return (await res.json()).data.token
}

export interface CreatedProduct {
  id: number
  name: string
  price: number
}

export async function createProduct(
  request: APIRequestContext,
  token: string,
  product: { name: string; price: number; inventory: number; category: string },
): Promise<CreatedProduct> {
  const res = await request.post('/api/v1/products', {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      name: product.name,
      brand: 'E2E',
      price: product.price,
      description: 'Created by the end-to-end tests.',
      inventory: product.inventory,
      category: { name: product.category },
    },
  })
  expect(res.status(), await res.text()).toBe(201)
  const { id } = (await res.json()).data
  return { id, name: product.name, price: product.price }
}

export interface Customer {
  email: string
  password: string
  token: string
}

/** A new customer, registered and signed in through the API. */
export async function createCustomer(request: APIRequestContext): Promise<Customer> {
  const email = `${unique('e2e').replace(' ', '-')}@example.com`
  const password = 'e2e-password-123'
  const registered = await request.post('/api/v1/users', {
    data: { firstName: 'Erin', lastName: 'Tester', email, password },
  })
  expect(registered.status(), await registered.text()).toBe(201)
  const res = await request.post('/api/v1/auth/login', { data: { email, password } })
  expect(res.status(), await res.text()).toBe(200)
  return { email, password, token: (await res.json()).data.token }
}

/** The customer's order of `quantity` of `product`, paid with the simulated provider's test Visa; its id. */
export async function createPaidOrder(
  request: APIRequestContext,
  customer: Customer,
  product: CreatedProduct,
  quantity: number,
): Promise<number> {
  const auth = { Authorization: `Bearer ${customer.token}` }
  const added = await request.post('/api/v1/cartItems', { headers: auth, params: { productId: product.id, quantity } })
  expect(added.status(), await added.text()).toBe(201)
  const placed = await request.post('/api/v1/orders', {
    headers: auth,
    data: {
      recipientName: 'Erin Tester',
      addressLine1: '1 Test Street',
      city: 'Testville',
      state: 'TS',
      postalCode: '12345',
      country: 'US',
    },
  })
  expect(placed.status(), await placed.text()).toBe(201)
  const orderId: number = (await placed.json()).data.id
  const paid = await request.post(`/api/v1/orders/${orderId}/payments`, {
    headers: { ...auth, 'Idempotency-Key': unique('pay').replace(' ', '-') },
    data: { paymentMethod: 'test_visa' },
  })
  expect(paid.status(), await paid.text()).toBe(201)
  expect((await paid.json()).data.status).toBe('SUCCEEDED')
  return orderId
}

export async function inventoryOf(request: APIRequestContext, productId: number): Promise<number> {
  const res = await request.get(`/api/v1/products/${productId}`)
  expect(res.status()).toBe(200)
  return (await res.json()).data.inventory
}
