import { request } from './client'
import type { ImageDto } from './types'

export const imagesApi = {
  /** POST /images — attach one or more images to a product (admin only, server-enforced). */
  upload: (productId: number, files: File[]) => {
    const form = new FormData()
    files.forEach((file) => form.append('files', file))
    return request<ImageDto[]>('/images', { method: 'POST', query: { productId }, body: form })
  },

  /** DELETE /images/{id} — admin only (server-enforced). */
  remove: (imageId: number) => request<null>(`/images/${imageId}`, { method: 'DELETE' }),
}
