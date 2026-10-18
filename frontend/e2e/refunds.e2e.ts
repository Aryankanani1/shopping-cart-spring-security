import type { Page } from '@playwright/test'
import {
  admin,
  adminToken,
  createCustomer,
  createPaidOrder,
  createProduct,
  expect,
  inventoryOf,
  test,
  unique,
} from './support'

// Partial refunds (docs/payment-design.md, section 11): the admin refunds part of a
// paid order, an item sent back and an amount, and the customer sees both on the
// order. The customer and the paid order are made through the API.

async function signIn(page: Page, user: { email: string; password: string }) {
  await page.goto('/login')
  await page.getByLabel('Email').fill(user.email)
  await page.getByLabel('Password').fill(user.password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('button', { name: 'Log out' })).toBeVisible()
}

test('the admin refunds a returned item and an amount, and the customer sees both on the order', async ({
  page,
  request,
}) => {
  const lamp = await createProduct(request, await adminToken(request), {
    name: unique('E2E Reading Lamp'),
    price: 30,
    inventory: 5,
    category: unique('E2E Returns'),
  })
  const customer = await createCustomer(request)
  const orderId = await createPaidOrder(request, customer, lamp, 2)
  expect(await inventoryOf(request, lamp.id)).toBe(3)

  const form = page.getByRole('form', { name: `Refund order #${orderId}` })

  await test.step('the admin opens the refund form of the order', async () => {
    await signIn(page, admin)
    await page.goto('/admin/orders')
    const row = page.getByRole('row').filter({ has: page.getByRole('cell', { name: `#${orderId}`, exact: true }) })
    await row.getByRole('button', { name: 'Refund' }).click()
    await expect(form.getByText('$60.00 of $60.00 is left to refund.')).toBeVisible()
  })

  await test.step('refunds one lamp the customer sent back, and puts it back in stock', async () => {
    await form.getByLabel(`How many ${lamp.name} to refund`).fill('1')
    await form.getByLabel('Put them back in stock').check()
    await form.getByLabel('Reason').fill('Returned: wrong colour')
    page.once('dialog', (dialog) => dialog.accept())
    await form.getByRole('button', { name: 'Refund $30.00' }).click()
    await expect(form.getByText('Refund of $30.00 requested.')).toBeVisible()
    // The provider makes the refund just after it is committed; the list checks back until it has.
    const refunds = form.getByRole('region', { name: 'Refunds' })
    await expect(refunds.getByText('refunded', { exact: true })).toBeVisible({ timeout: 15_000 })
    expect(await inventoryOf(request, lamp.id)).toBe(4)
  })

  await test.step('refunds an amount for a damaged box, up to what is left', async () => {
    await form.getByLabel('An amount').check()
    await form.getByLabel('Amount', { exact: true }).fill('5')
    await form.getByLabel('Reason').fill('The box arrived damaged')
    page.once('dialog', (dialog) => dialog.accept())
    await form.getByRole('button', { name: 'Refund $5.00' }).click()
    await expect(form.getByText('Refund of $5.00 requested.')).toBeVisible()
    await expect(form.getByText('$25.00 of $60.00 is left to refund.')).toBeVisible()
  })

  await test.step('the customer sees both refunds, and the lamp refunded, on the order', async () => {
    await page.getByRole('button', { name: 'Log out' }).click()
    await signIn(page, customer)
    await page.goto(`/orders/${orderId}`)
    const refunds = page.getByRole('region', { name: 'Refunds' })
    await expect(refunds.getByText('Returned: wrong colour')).toBeVisible()
    await expect(refunds.getByText(`1 × ${lamp.name}`)).toBeVisible()
    await expect(refunds.getByText('The box arrived damaged')).toBeVisible()
    await expect(refunds.getByText('refunded', { exact: true })).toHaveCount(2, { timeout: 15_000 })
    await expect(page.getByText('1 refunded', { exact: true })).toBeVisible()
    await expect(page.getByText('$35.00 refunded so far')).toBeVisible({ timeout: 15_000 })
  })
})
