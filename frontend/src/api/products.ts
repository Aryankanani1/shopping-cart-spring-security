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
}
