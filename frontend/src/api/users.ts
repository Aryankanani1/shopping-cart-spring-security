import { request } from './client'
import type { UserDto } from './types'

/** Editable profile fields (matches the backend UserUpdateRequest). */
export interface UpdateUserBody {
  firstName: string
  lastName: string
}

export const usersApi = {
  /**
   * GET /users/{id} — self only (enforced server-side). Returns the user with
   * their embedded cart; this is how the frontend resolves the current cart id,
   * since the add-to-cart endpoint takes no cartId.
   */
  get: (id: number) => request<UserDto>(`/users/${id}`),

  /** PUT /users/{id} — update the caller's own name (self only, server-enforced). */
  update: (id: number, body: UpdateUserBody) =>
    request<UserDto>(`/users/${id}`, { method: 'PUT', body }),

  /** DELETE /users/{id} — permanently delete the caller's own account. */
  remove: (id: number) => request<null>(`/users/${id}`, { method: 'DELETE' }),
}
