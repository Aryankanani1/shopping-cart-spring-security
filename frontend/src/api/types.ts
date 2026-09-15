// Wire types — mirror the backend DTOs / response envelopes exactly.
// (com.aryan.spring_security_demo.dto / .response)

/** Every success response is wrapped: { message, data }. */
export interface ApiResponse<T> {
  message: string
  data: T
}

/** Error responses are RFC 7807 problem+json (see GlobalExceptionHandler). */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  /** Field-level messages on 400 (validation). */
  errors?: Record<string, string>
}

export interface JwtResponse {
  id: number
  token: string
  refreshToken: string
}

export interface CategoryDto {
  id: number
  name: string
}

export interface ImageDto {
  imageId: number
  imageName: string
  downloadUrl: string
}

export interface ProductDto {
  id: number
  name: string
  brand: string
  price: number
  description: string
  inventory: number
  categoryName: string
  images: ImageDto[]
}

export interface CartItemDto {
  itemId: number
  quantity: number
  unitPrice: number
  product: ProductDto
}

export interface CartDto {
  cartId: number
  cartItems: CartItemDto[]
  totalAmount: number
}

export interface OrderItemDto {
  productId: number
  productName: string
  productBrand: string
  quantity: number
  price: number
}

export interface OrderDto {
  id: number
  userId: number
  /** ISO date, e.g. "2026-09-14". */
  orderDate: string
  totalAmount: number
  status: string
  items: OrderItemDto[]
}

export interface UserDto {
  id: number
  firstName: string
  lastName: string
  email: string
  orders?: OrderDto[]
  cart?: CartDto | null
}

/** Offset pagination envelope (products). */
export interface PagedResponse<T> {
  content: T[]
  number: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
  numberOfElements: number
}

/** Keyset (cursor) slice envelope (order history). */
export interface SlicedResponse<T> {
  content: T[]
  size: number
  numberOfElements: number
  hasNext: boolean
  nextCursor: string | null
}
