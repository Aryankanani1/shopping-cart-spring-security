import { adminToken, createProduct, expect, inventoryOf, test, unique, type CreatedProduct } from './support'

// The customer's path through the shop, in a real browser against the real stack:
// sign up, find a product, put it in the bag, check out, pay, cancel for a refund,
// and find the order again.

let product: CreatedProduct

test.beforeAll(async ({ request }) => {
  const token = await adminToken(request)
  product = await createProduct(request, token, {
    name: unique('E2E Lamp'),
    price: 49.5,
    inventory: 4,
    category: unique('E2E Lighting'),
  })
})

test('a new customer signs up, buys and pays for a product, cancels it for a refund, and finds the order', async ({ page, request }) => {
  const email = `${unique('e2e').replace(' ', '-')}@example.com`

  await test.step('sign up', async () => {
    await page.goto('/register')
    await page.getByLabel('First name').fill('Erin')
    await page.getByLabel('Last name').fill('Tester')
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Password').fill('e2e-password-123')
    await page.getByRole('button', { name: 'Create account' }).click()
    // Signing up also signs in.
    await expect(page).toHaveURL('/')
    await expect(page.getByRole('button', { name: 'Log out' })).toBeVisible()
  })

  await test.step('put the product in the bag', async () => {
    await page.goto(`/products/${product.id}`)
    await expect(page.getByRole('heading', { name: product.name, exact: true })).toBeVisible()
    await page.getByRole('button', { name: 'Add to bag' }).click()
    await expect(page.getByText('Added to your bag.')).toBeVisible()
    await page.getByRole('link', { name: 'View bag →' }).click()
    await expect(page).toHaveURL('/cart')
    // The line's name link (its picture links to the product too).
    await expect(page.getByRole('link', { name: product.name, exact: true }).first()).toBeVisible()
  })

  let orderUrl = ''
  await test.step('check out', async () => {
    await page.getByRole('button', { name: 'Checkout' }).click()
    await expect(page).toHaveURL('/checkout')
    await page.getByLabel('Recipient name').fill('Erin Tester')
    await page.getByLabel('Address line 1').fill('1 Test Street')
    await page.getByLabel('City').fill('Testville')
    await page.getByLabel('State / region').fill('TS')
    await page.getByLabel('Postal code').fill('12345')
    await page.getByLabel('Country').fill('US')
    await page.getByRole('button', { name: 'Place order' }).click()
    await expect(page).toHaveURL(/\/orders\/\d+$/)
    await expect(page.getByText('Thank you — your order has been placed.')).toBeVisible()
    orderUrl = new URL(page.url()).pathname
  })

  await test.step('the order took the item out of stock', async () => {
    expect(await inventoryOf(request, product.id)).toBe(3)
  })

  await test.step('a declined test card fails, and a test Visa pays the order', async () => {
    // Unpaid, the order says when it will be cancelled.
    await expect(page.getByText(/^Pay by /)).toBeVisible()
    const pay = page.getByRole('button', { name: 'Pay $49.50' })
    await page.getByLabel('Test card').selectOption('test_declined')
    await pay.click()
    await expect(page.getByText('Payment failed: Your card was declined.')).toBeVisible()

    await page.getByLabel('Test card').selectOption('test_visa')
    await pay.click()
    // Paid: the attempt is listed, and there is nothing left to pay.
    await expect(page.getByText('Visa ending 4242')).toBeVisible()
    await expect(page.getByRole('button', { name: /^Pay / })).toHaveCount(0)
  })

  await test.step('cancelling the paid order refunds it and puts the item back in stock', async () => {
    page.once('dialog', (dialog) => dialog.accept())
    await page.getByRole('button', { name: 'Cancel order' }).click()
    // The refund runs just after the cancellation commits; the page checks back until it's settled.
    await expect(page.getByText('refunded', { exact: true })).toBeVisible({ timeout: 15_000 })
    expect(await inventoryOf(request, product.id)).toBe(4)
  })

  await test.step('the order is listed under My orders', async () => {
    await page.goto('/orders')
    await expect(page.locator(`a[href="${orderUrl}"]`).first()).toBeVisible()
  })
})

test('a visitor who is not signed in is sent to sign in before checkout', async ({ page }) => {
  await page.goto('/checkout')
  await expect(page).toHaveURL('/login')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
})
