import { request } from './client'
import type { CategoryDto } from './types'

export const categoriesApi = {
  list: () => request<CategoryDto[]>('/categories', { auth: false }),
}
