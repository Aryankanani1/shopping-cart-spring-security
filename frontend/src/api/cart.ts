import { request } from './client'
import type { CartDto } from './types'

// Note: add-to-cart takes no cartId — the backend resolves (and lazily creates)
// the authenticated user's cart. Mutations that DO need the cartId get it from
// the user's embedded cart (usersApi.get -> cart.cartId).
export const cartApi = {
  addItem: (productId: number, quantity: number) =>
    request<null>('/cartItems', { method: 'POST', query: { productId, quantity } }),

  updateQuantity: (cartId: number, itemId: number, quantity: number) =>
    request<null>(`/cartItems/cart/${cartId}/item/${itemId}`, { method: 'PUT', query: { quantity } }),

  removeItem: (cartId: number, productId: number) =>
    request<null>(`/cartItems/cart/${cartId}/product/${productId}`, { method: 'DELETE' }),

  clear: (cartId: number) => request<null>(`/carts/${cartId}/items`, { method: 'DELETE' }),

  get: (cartId: number) => request<CartDto>(`/carts/${cartId}`),
}
