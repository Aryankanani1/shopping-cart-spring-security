import { request } from './client'
import type { CategoryDto } from './types'

export const categoriesApi = {
  list: () => request<CategoryDto[]>('/categories', { auth: false }),

  /** POST /categories — admin only (server-enforced). */
  create: (name: string) => request<CategoryDto>('/categories', { method: 'POST', body: { name } }),

  /** PUT /categories/{id} — admin only (server-enforced). */
  update: (id: number, name: string) =>
    request<CategoryDto>(`/categories/${id}`, { method: 'PUT', body: { name } }),

  /** DELETE /categories/{id} — admin only (server-enforced). */
  remove: (id: number) => request<null>(`/categories/${id}`, { method: 'DELETE' }),
}
