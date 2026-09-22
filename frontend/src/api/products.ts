import { request } from './client'
import type { PagedResponse, ProductDto } from './types'

export interface ProductQuery {
  brand?: string
  name?: string
  /** Category NAME (the backend filters by name, not id). */
  category?: string
  /** Zero-based page index. */
  page?: number
  size?: number
  /** e.g. "price,asc" — allowed fields: id, name, price, brand. */
  sort?: string
}

export const productsApi = {
  list: (query: ProductQuery = {}) =>
    request<PagedResponse<ProductDto>>('/products', { auth: false, query: { ...query } }),

  get: (id: number) => request<ProductDto>(`/products/${id}`, { auth: false }),

  /** POST /products — admin only (server-enforced). */
  create: (input: ProductInput) =>
    request<ProductDto>('/products', { method: 'POST', body: toRequestBody(input) }),

  /** PUT /products/{id} — admin only (server-enforced). */
  update: (id: number, input: ProductInput) =>
    request<ProductDto>(`/products/${id}`, { method: 'PUT', body: toRequestBody(input) }),

  /** DELETE /products/{id} — admin only (server-enforced). */
  remove: (id: number) => request<null>(`/products/${id}`, { method: 'DELETE' }),
}

/** Writable product fields for admin create/edit (matches Add/Update ProductRequest). */
export interface ProductInput {
  name: string
  brand: string
  price: number
  description: string
  inventory: number
  /** Category NAME — the backend resolves/creates the category by name. */
  categoryName: string
}

/** Shape the flat form input into the backend's nested-category request body. */
function toRequestBody(input: ProductInput) {
  return {
    name: input.name,
    brand: input.brand,
    price: input.price,
    description: input.description,
    inventory: input.inventory,
    category: { name: input.categoryName },
  }
}
