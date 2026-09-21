import { createContext, useCallback, useContext, useMemo } from 'react'
import type { ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { cartApi } from '../api/cart'
import { usersApi } from '../api/users'
import type { CartDto } from '../api/types'
import { useAuth } from './AuthContext'
import { queryKeys } from '../api/queryKeys'

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

// --- pure cache transforms, used for optimistic updates ---------------------
// The server stays the source of truth (every mutation revalidates onSettled),
// but applying the change locally first makes the UI feel instant instead of
// waiting on a round-trip plus a full cart refetch.
function recomputeTotal(items: CartDto['cartItems']): number {
  return items.reduce((sum, i) => sum + i.unitPrice * i.quantity, 0)
}
function withQuantity(cart: CartDto, itemId: number, quantity: number): CartDto {
  const cartItems = cart.cartItems.map((i) => (i.itemId === itemId ? { ...i, quantity } : i))
  return { ...cart, cartItems, totalAmount: recomputeTotal(cartItems) }
}
function withoutProduct(cart: CartDto, productId: number): CartDto {
  const cartItems = cart.cartItems.filter((i) => i.product.id !== productId)
  return { ...cart, cartItems, totalAmount: recomputeTotal(cartItems) }
}
function emptied(cart: CartDto): CartDto {
  return { ...cart, cartItems: [], totalAmount: 0 }
}

export function CartProvider({ children }: { children: ReactNode }) {
  const { userId } = useAuth()
  const queryClient = useQueryClient()
  const cartKey = queryKeys.cart(userId)

  // The cart id isn't known up front (add-to-cart takes none), so resolve the
  // whole cart from the user record. React Query keys it by user, caches it and
  // dedupes concurrent readers — no more manual refresh() wiring in every page.
  const {
    data: cart = null,
    isLoading,
    refetch,
  } = useQuery({
    queryKey: cartKey,
    queryFn: async () => {
      const user = await usersApi.get(userId as number)
      return user.cart ?? null
    },
    enabled: userId != null,
  })

  const invalidate = useCallback(
    () => queryClient.invalidateQueries({ queryKey: cartKey }),
    [queryClient, cartKey],
  )

  // Snapshot the cached cart, apply an optimistic transform, and return a context
  // that onError can roll back to. Shared by the three mutations that carry all
  // the data they need to update the cache locally.
  const snapshotAndApply = useCallback(
    async (transform: (cart: CartDto) => CartDto) => {
      await queryClient.cancelQueries({ queryKey: cartKey })
      const previous = queryClient.getQueryData<CartDto | null>(cartKey)
      if (previous) queryClient.setQueryData<CartDto | null>(cartKey, transform(previous))
      return { previous }
    },
    [queryClient, cartKey],
  )
  const rollback = useCallback(
    (ctx?: { previous: CartDto | null | undefined }) =>
      queryClient.setQueryData(cartKey, ctx?.previous ?? null),
    [queryClient, cartKey],
  )

  // Add can't be applied optimistically — we don't yet know the new line's
  // itemId or the server-computed unit price — so it just invalidates on success.
  const { mutateAsync: addAsync } = useMutation({
    mutationFn: ({ productId, quantity }: { productId: number; quantity: number }) =>
      cartApi.addItem(productId, quantity),
    onSuccess: invalidate,
  })

  const { mutateAsync: setQtyAsync } = useMutation({
    mutationFn: ({ itemId, quantity }: { itemId: number; quantity: number }) =>
      cartApi.updateQuantity((cart as CartDto).cartId, itemId, quantity),
    onMutate: ({ itemId, quantity }) => snapshotAndApply((c) => withQuantity(c, itemId, quantity)),
    onError: (_e, _v, ctx) => rollback(ctx),
    onSettled: invalidate,
  })

  const { mutateAsync: removeAsync } = useMutation({
    mutationFn: (productId: number) => cartApi.removeItem((cart as CartDto).cartId, productId),
    onMutate: (productId) => snapshotAndApply((c) => withoutProduct(c, productId)),
    onError: (_e, _v, ctx) => rollback(ctx),
    onSettled: invalidate,
  })

  const { mutateAsync: clearAsync } = useMutation({
    mutationFn: () => cartApi.clear((cart as CartDto).cartId),
    onMutate: () => snapshotAndApply(emptied),
    onError: (_e, _v, ctx) => rollback(ctx),
    onSettled: invalidate,
  })

  const addItem = useCallback(
    async (productId: number, quantity: number) => {
      await addAsync({ productId, quantity })
    },
    [addAsync],
  )
  const setQuantity = useCallback(
    async (itemId: number, quantity: number) => {
      if (!cart) return
      await setQtyAsync({ itemId, quantity })
    },
    [cart, setQtyAsync],
  )
  const removeItem = useCallback(
    async (productId: number) => {
      if (!cart) return
      await removeAsync(productId)
    },
    [cart, removeAsync],
  )
  const clear = useCallback(async () => {
    if (!cart) return
    await clearAsync()
  }, [cart, clearAsync])
  const refresh = useCallback(async () => {
    if (userId != null) await refetch()
  }, [userId, refetch])

  const itemCount = useMemo(
    () => (cart?.cartItems ?? []).reduce((sum, item) => sum + item.quantity, 0),
    [cart],
  )

  const value = useMemo<CartContextValue>(
    () => ({ cart, itemCount, loading: isLoading, refresh, addItem, setQuantity, removeItem, clear }),
    [cart, itemCount, isLoading, refresh, addItem, setQuantity, removeItem, clear],
  )

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

export function useCart(): CartContextValue {
  const ctx = useContext(CartContext)
  if (!ctx) throw new Error('useCart must be used within a CartProvider')
  return ctx
}
