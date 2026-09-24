import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ProductImages } from './ProductImages'
import type { ProductDto } from '../../api/types'

vi.mock('../../api/products', () => ({
  productsApi: { get: vi.fn() },
}))
vi.mock('../../api/images', () => ({
  imagesApi: { upload: vi.fn(), remove: vi.fn() },
}))

import { productsApi } from '../../api/products'
import { imagesApi } from '../../api/images'

const PRODUCT: ProductDto = {
  id: 5,
  name: 'Desk Lamp',
  brand: 'Lumo',
  price: 24,
  description: '',
  inventory: 3,
  categoryName: 'Electronics',
  images: [{ imageId: 11, imageName: 'lamp.png', downloadUrl: '/api/v1/images/11' }],
}

function renderImages() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ProductImages productId={5} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.mocked(productsApi.get).mockReset().mockResolvedValue(PRODUCT)
  vi.mocked(imagesApi.upload).mockReset().mockResolvedValue([])
  vi.mocked(imagesApi.remove).mockReset().mockResolvedValue(null)
})

describe('ProductImages', () => {
  it("shows the product's current images", async () => {
    renderImages()
    const thumb = await screen.findByAltText<HTMLImageElement>('lamp.png')
    expect(thumb.getAttribute('src')).toBe('/api/v1/images/11')
    expect(productsApi.get).toHaveBeenCalledWith(5)
  })

  it('uploads the chosen files to the product', async () => {
    renderImages()
    const input = await screen.findByLabelText<HTMLInputElement>('Add images')
    const file = new File(['png'], 'new.png', { type: 'image/png' })

    fireEvent.change(input, { target: { files: [file] } })

    await waitFor(() => expect(imagesApi.upload).toHaveBeenCalledWith(5, [file]))
  })

  it('removes an image after confirmation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderImages()
    await screen.findByAltText('lamp.png')

    fireEvent.click(screen.getByRole('button', { name: 'Remove' }))

    await waitFor(() => expect(imagesApi.remove).toHaveBeenCalled())
    expect(vi.mocked(imagesApi.remove).mock.calls[0][0]).toBe(11)
  })

  it('shows the API error when an upload is rejected', async () => {
    vi.mocked(imagesApi.upload).mockRejectedValue(new Error('page.html is not a supported image'))
    renderImages()
    const input = await screen.findByLabelText('Add images')

    fireEvent.change(input, { target: { files: [new File(['x'], 'page.html', { type: 'text/html' })] } })

    expect(await screen.findByText('page.html is not a supported image')).toBeInTheDocument()
  })
})
