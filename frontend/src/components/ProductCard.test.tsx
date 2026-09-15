import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ProductCard } from './ProductCard'
import type { ProductDto } from '../api/types'

function make(overrides: Partial<ProductDto> = {}): ProductDto {
  return {
    id: 5,
    name: 'Enamel Kettle',
    brand: 'Falcon',
    price: 48,
    description: 'A sturdy kettle.',
    inventory: 3,
    categoryName: 'Home & Kitchen',
    images: [{ imageId: 11, imageName: 'kettle.jpg', downloadUrl: '/ignored' }],
    ...overrides,
  }
}

function renderCard(product: ProductDto) {
  return render(
    <MemoryRouter>
      <ProductCard product={product} />
    </MemoryRouter>,
  )
}

describe('ProductCard', () => {
  it('renders name, brand and formatted price, and links to the detail page', () => {
    renderCard(make())
    expect(screen.getByText('Enamel Kettle')).toBeInTheDocument()
    expect(screen.getByText('Falcon')).toBeInTheDocument()
    expect(screen.getByText('$48.00')).toBeInTheDocument()
    expect(screen.getByRole('link')).toHaveAttribute('href', '/products/5')
  })

  it('builds the image src from the image id (not the stored downloadUrl)', () => {
    renderCard(make())
    expect(screen.getByRole('img')).toHaveAttribute('src', '/api/v1/images/11')
  })

  it('shows a placeholder and no <img> when there are no images', () => {
    renderCard(make({ images: [] }))
    expect(screen.queryByRole('img')).toBeNull()
  })

  it('flags sold-out products', () => {
    renderCard(make({ inventory: 0 }))
    expect(screen.getByText('Sold out')).toBeInTheDocument()
  })
})
