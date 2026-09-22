import type { ProductQuery } from './products'

/**
 * Central registry of React Query cache keys. Keeping them in one factory (rather
 * than inline string arrays scattered across hooks) means a mutation can
 * invalidate exactly the right slice, and two components asking for the same data
 * share one cache entry (dedup) — e.g. the category list on the Home and Shop
 * pages resolves to a single request.
 */
export const queryKeys = {
  products: {
    all: ['products'] as const,
    list: (query: ProductQuery) => ['products', 'list', query] as const,
    detail: (id: number) => ['products', 'detail', id] as const,
  },
  categories: ['categories'] as const,
  user: (userId: number | null) => ['user', userId] as const,
  cart: (userId: number | null) => ['cart', userId] as const,
  orders: {
    all: ['orders'] as const,
    history: (userId: number | null) => ['orders', 'history', userId] as const,
    detail: (id: number) => ['orders', 'detail', id] as const,
    adminList: (page: number) => ['orders', 'admin', page] as const,
  },
  admin: {
    products: (page: number) => ['admin', 'products', page] as const,
    categories: ['admin', 'categories'] as const,
  },
} as const
