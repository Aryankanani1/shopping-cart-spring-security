import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { cartApi } from '../api/cart'
import { usersApi } from '../api/users'
import type { CartDto } from '../api/types'
import { useAuth } from './AuthContext'

interface CartContextValue {
  cart: CartDto | null
  itemCount: number
  loading: boolean
  refresh: () => Promise<void>
  addItem: (productId: number, quantity: number) => Promise<void>
  setQuantity: (itemId: number, quantity: number) => Promise<void>
  removeItem: (productId: number) => Promise<void>
  clear: () => Promise<void>
}

const CartContext = createContext<CartContextValue | null>(null)

export function CartProvider({ children }: { children: ReactNode }) {
  const { userId } = useAuth()
  const [cart, setCart] = useState<CartDto | null>(null)
  const [loading, setLoading] = useState(false)

  // The cart id isn't known up front (add-to-cart takes none), so resolve the
  // whole cart from the user record. Re-run whenever the signed-in user changes.
  const refresh = useCallback(async () => {
    if (!userId) {
      setCart(null)
      return
    }
    setLoading(true)
    try {
      const user = await usersApi.get(userId)
      setCart(user.cart ?? null)
    } finally {
      setLoading(false)
    }
  }, [userId])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const addItem = useCallback(
    async (productId: number, quantity: number) => {
      await cartApi.addItem(productId, quantity)
      await refresh()
    },
    [refresh],
  )

  const setQuantity = useCallback(
    async (itemId: number, quantity: number) => {
      if (!cart) return
      await cartApi.updateQuantity(cart.cartId, itemId, quantity)
      await refresh()
    },
    [cart, refresh],
  )

  const removeItem = useCallback(
    async (productId: number) => {
      if (!cart) return
      await cartApi.removeItem(cart.cartId, productId)
      await refresh()
    },
    [cart, refresh],
  )

  const clear = useCallback(async () => {
    if (!cart) return
    await cartApi.clear(cart.cartId)
    await refresh()
  }, [cart, refresh])

  const itemCount = useMemo(
    () => (cart?.cartItems ?? []).reduce((sum, item) => sum + item.quantity, 0),
    [cart],
  )

  const value = useMemo<CartContextValue>(
    () => ({ cart, itemCount, loading, refresh, addItem, setQuantity, removeItem, clear }),
    [cart, itemCount, loading, refresh, addItem, setQuantity, removeItem, clear],
  )

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

export function useCart(): CartContextValue {
  const ctx = useContext(CartContext)
  if (!ctx) throw new Error('useCart must be used within a CartProvider')
  return ctx
}
