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

export async function inventoryOf(request: APIRequestContext, productId: number): Promise<number> {
  const res = await request.get(`/api/v1/products/${productId}`)
  expect(res.status()).toBe(200)
  return (await res.json()).data.inventory
}
