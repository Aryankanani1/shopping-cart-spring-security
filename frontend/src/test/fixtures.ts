import type {
  CartDto,
  OrderDto,
  OrderSummaryDto,
  PagedResponse,
  ProductDto,
} from '../api/types'

export function product(overrides: Partial<ProductDto> = {}): ProductDto {
  return {
    id: 1,
    name: 'Desk Lamp',
    brand: 'Acme',
    price: 24,
    description: 'An LED desk lamp.',
    inventory: 10,
    categoryName: 'Lighting',
    images: [],
    ...overrides,
  }
}

/** A cart whose lines are `[itemId, productId, quantity, unitPrice]`. */
export function cartWith(lines: Array<[number, number, number, number]>): CartDto {
  return {
    cartId: 500,
    totalAmount: lines.reduce((sum, [, , qty, price]) => sum + qty * price, 0),
    cartItems: lines.map(([itemId, productId, quantity, unitPrice]) => ({
      itemId,
      quantity,
      unitPrice,
      product: product({ id: productId, name: `Product ${productId}`, price: unitPrice }),
    })),
  }
}

export function order(overrides: Partial<OrderDto> = {}): OrderDto {
  return {
    id: 55,
    userId: 7,
    orderDate: '2026-09-14',
    totalAmount: 48,
    status: 'PENDING',
    recipientName: 'Ada Lovelace',
    addressLine1: '1 Analytical Way',
    city: 'London',
    state: 'LDN',
    postalCode: 'EC1A',
    country: 'UK',
    items: [{ productId: 1, productName: 'Desk Lamp', productBrand: 'Acme', quantity: 2, price: 24 }],
    ...overrides,
  }
}

export function orderSummary(overrides: Partial<OrderSummaryDto> = {}): OrderSummaryDto {
  return {
    id: 55,
    userId: 7,
    userEmail: 'ada@example.com',
    orderDate: '2026-09-14',
    totalAmount: 48,
    status: 'PENDING',
    ...overrides,
  }
}

/** One page of results; `page`/`totalPages` default to a single page. */
export function paged<T>(content: T[], page = 0, totalPages = 1): PagedResponse<T> {
  return {
    content,
    number: page,
    size: 20,
    totalElements: content.length,
    totalPages,
    first: page === 0,
    last: page >= totalPages - 1,
    numberOfElements: content.length,
  }
}
