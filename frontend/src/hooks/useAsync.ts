import { useEffect, useState } from 'react'
import { ApiError } from '../api/client'

export interface AsyncState<T> {
  data: T | null
  loading: boolean
  error: string | null
}

/** Turn any error into a display string, preferring the API's problem detail. */
export function errMessage(err: unknown): string {
  if (err instanceof ApiError) return err.message
  if (err instanceof Error) return err.message
  return 'Something went wrong. Please try again.'
}

/**
 * Run an async loader on mount and whenever `deps` change, tracking
 * loading/error and ignoring results from a stale/unmounted run.
 */
export function useAsync<T>(
  loader: () => Promise<T>,
  deps: unknown[],
): AsyncState<T> & { reload: () => void } {
  const [state, setState] = useState<AsyncState<T>>({ data: null, loading: true, error: null })
  const [nonce, setNonce] = useState(0)

  useEffect(() => {
    let active = true
    setState((prev) => ({ ...prev, loading: true, error: null }))
    loader().then(
      (data) => {
        if (active) setState({ data, loading: false, error: null })
      },
      (err) => {
        if (active) setState({ data: null, loading: false, error: errMessage(err) })
      },
    )
    return () => {
      active = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce])

  return { ...state, reload: () => setNonce((n) => n + 1) }
}
