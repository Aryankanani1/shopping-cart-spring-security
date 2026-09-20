import { ApiError } from '../api/client'

/**
 * Turn any thrown value into a display string, preferring the API's problem+json
 * message. Used for mutation/action error handling (React Query surfaces errors
 * as {@code unknown}, same as a raw try/catch).
 */
export function errMessage(err: unknown): string {
  if (err instanceof ApiError) return err.message
  if (err instanceof Error) return err.message
  return 'Something went wrong. Please try again.'
}
