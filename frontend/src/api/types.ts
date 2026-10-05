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

/** Shipping address captured at checkout and stored on the order. */
export interface ShippingAddress {
  recipientName: string
  addressLine1: string
  addressLine2?: string
  city: string
  state: string
  postalCode: string
  country: string
}

export interface OrderDto {
  id: number
  userId: number
  /** ISO date, e.g. "2026-09-14". */
  orderDate: string
  totalAmount: number
  status: string
  recipientName?: string
  addressLine1?: string
  addressLine2?: string
  city?: string
  state?: string
  postalCode?: string
  country?: string
  items: OrderItemDto[]
}

/** Lightweight order row for the admin order list (no item breakdown). */
export interface OrderSummaryDto {
  id: number
  userId: number
  userEmail: string
  /** ISO date, e.g. "2026-09-14". */
  orderDate: string
  totalAmount: number
  status: string
}

export interface UserDto {
  id: number
  firstName: string
  lastName: string
  email: string
  cart?: CartDto | null
}

export interface WishlistItemDto {
  product: ProductDto
  /** ISO instant. */
  addedAt: string
  priceWhenAdded: number
  /** ISO instant of a pending reminder; absent when none is set. */
  remindAt?: string | null
  /** Price-drop and back-in-stock alerts. */
  alertsEnabled: boolean
}

export type NotificationType = 'WISHLIST_REMINDER' | 'PRICE_DROP' | 'BACK_IN_STOCK'

/** In-app inbox entry. Structured facts — the client writes the sentence. */
export interface NotificationDto {
  id: number
  type: NotificationType
  /** Absent once the product has been deleted; productName is always present. */
  productId?: number | null
  productName: string
  /** PRICE_DROP only. */
  oldPrice?: number | null
  newPrice?: number | null
  /** ISO instant. */
  createdAt: string
  read: boolean
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
