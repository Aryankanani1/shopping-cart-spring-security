import { expect, test } from '@playwright/test'
import { admin, adminToken, unique } from './support'

// The admin's path: sign in, add a product to the catalogue, and see customers
// find it in the storefront.

let category: string

test.beforeAll(async ({ request }) => {
  // The product form picks its category from a list of existing ones.
  category = unique('E2E Kitchen')
  const res = await request.post('/api/v1/categories', {
    headers: { Authorization: `Bearer ${await adminToken(request)}` },
    data: { name: category },
  })
  expect(res.status(), await res.text()).toBe(201)
})

test('the admin adds a product that customers can then find', async ({ page }) => {
  const name = unique('E2E Kettle')

  await test.step('sign in as the admin', async () => {
    await page.goto('/login')
    await page.getByLabel('Email').fill(admin.email)
    await page.getByLabel('Password').fill(admin.password)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByRole('link', { name: 'Admin' })).toBeVisible()
  })

  await test.step('create the product', async () => {
    await page.getByRole('link', { name: 'Admin' }).click()
    await expect(page.getByRole('heading', { name: 'Store management' })).toBeVisible()
    // The admin's Products tab, not the storefront's Products link in the header.
    await page.getByRole('main').getByRole('link', { name: 'Products' }).click()
    await expect(page).toHaveURL('/admin/products')
    await page.getByRole('button', { name: '+ New product' }).click()
    await page.getByLabel('Name').fill(name)
    await page.getByLabel('Brand').fill('E2E')
    await page.getByLabel('Price').fill('35')
    await page.getByLabel('Inventory').fill('7')
    await page.getByLabel('Category').selectOption(category)
    await page.getByLabel('Description').fill('Added by the end-to-end tests.')
    await page.getByRole('button', { name: 'Create product' }).click()
    await expect(page.getByText(name)).toBeVisible()
  })

  await test.step('a customer finds it in the storefront', async () => {
    await page.getByRole('button', { name: 'Log out' }).click()
    await page.goto('/products')
    await page.getByLabel('Search products').fill(name)
    await expect(page.getByText(name)).toBeVisible()
  })
})

test('the admin pages send a visitor to sign in', async ({ page }) => {
  await page.goto('/admin/products')
  await expect(page).toHaveURL('/login')
})
