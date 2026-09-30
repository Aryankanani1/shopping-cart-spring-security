import { useCallback, useMemo } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { wishlistApi } from '../api/wishlist'
import { queryKeys } from '../api/queryKeys'
import type { WishlistItemDto } from '../api/types'
import { useAuth } from '../context/AuthContext'

/**
 * The signed-in user's wishlist, shared through the React Query cache: the
 * product page's save button and the wishlist page read the same entry, so
 * saving on one shows up on the other without a refetch. Every mutation writes
 * the server's answer (the updated item) straight into the cache.
 */
export function useWishlist() {
  const { userId } = useAuth()
  const queryClient = useQueryClient()
  const key = queryKeys.wishlist(userId)

  const { data: items = [], isLoading, error } = useQuery({
    queryKey: key,
    queryFn: () => wishlistApi.list(),
    enabled: userId != null,
  })

  const upsert = useCallback(
    (item: WishlistItemDto) =>
      queryClient.setQueryData<WishlistItemDto[]>(key, (old = []) =>
        old.some((i) => i.product.id === item.product.id)
          ? old.map((i) => (i.product.id === item.product.id ? item : i))
          : [item, ...old],
      ),
    [queryClient, key],
  )

  const { mutateAsync: add } = useMutation({
    mutationFn: (productId: number) => wishlistApi.add(productId),
    onSuccess: upsert,
  })
  const { mutateAsync: remove } = useMutation({
    mutationFn: (productId: number) => wishlistApi.remove(productId),
    onSuccess: (_d, productId) =>
      queryClient.setQueryData<WishlistItemDto[]>(key, (old = []) =>
        old.filter((i) => i.product.id !== productId),
      ),
  })
  const { mutateAsync: setReminder } = useMutation({
    mutationFn: ({ productId, remindAt }: { productId: number; remindAt: string }) =>
      wishlistApi.setReminder(productId, remindAt),
    onSuccess: upsert,
  })
  const { mutateAsync: clearReminder } = useMutation({
    mutationFn: (productId: number) => wishlistApi.clearReminder(productId),
    onSuccess: upsert,
  })
  const { mutateAsync: setAlerts } = useMutation({
    mutationFn: ({ productId, enabled }: { productId: number; enabled: boolean }) =>
      wishlistApi.setAlerts(productId, enabled),
    onSuccess: upsert,
  })

  const savedIds = useMemo(() => new Set(items.map((i) => i.product.id)), [items])

  return {
    items,
    isLoading,
    error,
    isSaved: (productId: number) => savedIds.has(productId),
    add,
    remove,
    setReminder,
    clearReminder,
    setAlerts,
  }
}
