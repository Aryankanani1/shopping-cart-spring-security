import { adminToken, createCustomer, createOrder, createProduct, expect, signIn, test, unique } from './support'

// A charge after the payment failed (docs/payment-design.md, section 14): the
// simulated provider declines test_late_charge, then charges it after all and says
// so by webhook a few seconds later. The shop refunds all of it, and the order is
// still the customer's to pay. The customer and the order are made through the API.

test('a payment charged after it failed is refunded, and the order can still be paid', async ({ page, request }) => {
  const mug = await createProduct(request, await adminToken(request), {
    name: unique('E2E Travel Mug'),
    price: 12,
    inventory: 5,
    category: unique('E2E Late Charges'),
  })
  const customer = await createCustomer(request)
  const orderId = await createOrder(request, customer, mug, 1)
  await signIn(page, customer)
  await page.goto(`/orders/${orderId}`)
  const pay = page.getByRole('button', { name: 'Pay $12.00' })

  await test.step('the card is declined', async () => {
    await page.getByLabel('Test card').selectOption('test_late_charge')
    await pay.click()
    await expect(page.getByText("Payment failed: Your bank didn't confirm the payment in time.")).toBeVisible()
  })

  await test.step('the charge the provider reports later is refunded in full', async () => {
    // Reported about 5 seconds after the decline, then refunded through the outbox.
    const refunds = page.getByRole('region', { name: 'Refunds' })
    await expect(async () => {
      await page.reload()
      await expect(refunds.getByText('Charged after the payment failed')).toBeVisible({ timeout: 1_000 })
      await expect(refunds.getByText('refunded', { exact: true })).toBeVisible({ timeout: 1_000 })
    }).toPass({ timeout: 30_000 })
    await expect(refunds.getByText('$12.00')).toBeVisible()
  })

  await test.step('the order is still unpaid, and a test Visa pays it', async () => {
    await page.getByLabel('Test card').selectOption('test_visa')
    await pay.click()
    await expect(page.getByText('Visa ending 4242', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: /^Pay / })).toHaveCount(0)
  })
})
