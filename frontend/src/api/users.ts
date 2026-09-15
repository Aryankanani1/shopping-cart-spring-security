import { request } from './client'
import type { UserDto } from './types'

export const usersApi = {
  /**
   * GET /users/{id} — self only (enforced server-side). Returns the user with
   * their embedded cart; this is how the frontend resolves the current cart id,
   * since the add-to-cart endpoint takes no cartId.
   */
  get: (id: number) => request<UserDto>(`/users/${id}`),
}
