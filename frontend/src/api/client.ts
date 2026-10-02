import type { ApiResponse, JwtResponse, ProblemDetail } from './types'
import { getSession, setSession, syncFromStorage } from './tokenStore'

// In dev, VITE_API_BASE_URL is empty and calls go to /api/v1 — the Vite proxy
// forwards to :8082 (same origin, no CORS). In prod, set it to the API origin.
const API_ROOT = (import.meta.env.VITE_API_BASE_URL ?? '') + '/api/v1'

export type QueryValue = string | number | boolean | null | undefined

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  /**
   * Request body. A FormData is sent as-is (multipart, e.g. image uploads);
   * anything else is serialized and sent as application/json.
   */
  body?: unknown
  /** Query params; nullish and empty-string values are dropped. */
  query?: Record<string, QueryValue>
  /** Attach the bearer token (default true). */
  auth?: boolean
}

/** A failed request, carrying the parsed problem+json details. */
export class ApiError extends Error {
  status: number
  title?: string
  detail?: string
  /** Per-field messages from a 400 validation failure. */
  fieldErrors?: Record<string, string>

  constructor(
    status: number,
    message: string,
    opts: { title?: string; detail?: string; fieldErrors?: Record<string, string> } = {},
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.title = opts.title
    this.detail = opts.detail
    this.fieldErrors = opts.fieldErrors
  }
}

/** Absolute URL for a product image, served publicly by the API. */
export function imageUrl(imageId: number): string {
  return `${import.meta.env.VITE_API_BASE_URL ?? ''}/api/v1/images/${imageId}`
}

function buildQuery(query?: Record<string, QueryValue>): string {
  if (!query) return ''
  const pairs = Object.entries(query)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
  return pairs.length ? `?${pairs.join('&')}` : ''
}

async function toApiError(res: Response): Promise<ApiError> {
  let title: string | undefined
  let detail: string | undefined
  let fieldErrors: Record<string, string> | undefined
  try {
    const pd = (await res.json()) as ProblemDetail
    title = pd.title
    detail = pd.detail
    fieldErrors = pd.errors
  } catch {
    /* body was empty or not JSON */
  }
  const message = detail || title || res.statusText || `Request failed (${res.status})`
  return new ApiError(res.status, message, { title, detail, fieldErrors })
}

async function parseBody<T>(res: Response): Promise<T> {
  if (res.status === 204) return undefined as T
  const text = await res.text()
  if (!text) return undefined as T
  const json = JSON.parse(text) as ApiResponse<T>
  return json.data
}

// --- single-flight refresh -------------------------------------------------
// Many requests can 401 at once when the access token expires; collapse their
// refresh attempts into one network call so we rotate the refresh token once.
// Other tabs share the stored session and expire at the same moment, so the
// refresh also holds a cross-tab lock: a tab that waited for it finds the pair
// the other tab already got and adopts it. Presenting the spent refresh token
// instead would look like a stolen-token replay, and the server would end every
// session.
let refreshing: Promise<boolean> | null = null

async function tryRefresh(rejectedToken: string): Promise<boolean> {
  if (refreshing) return refreshing
  refreshing = withRefreshLock(() => refreshSession(rejectedToken))
  try {
    return await refreshing
  } finally {
    refreshing = null
  }
}

async function withRefreshLock(refresh: () => Promise<boolean>): Promise<boolean> {
  // Web Locks are in every current browser; where they're missing (jsdom in
  // tests, very old browsers) the refresh is still single-flight within the tab.
  if (!navigator.locks) return refresh()
  return navigator.locks.request('meridian.auth.refresh', refresh)
}

async function refreshSession(rejectedToken: string): Promise<boolean> {
  // Another tab (or an earlier refresh in this one) may have rotated already.
  const current = syncFromStorage()
  if (!current?.refreshToken) return false
  if (current.token !== rejectedToken) return true

  try {
    const res = await fetch(`${API_ROOT}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: current.refreshToken }),
    })
    if (!res.ok) {
      setSession(null)
      return false
    }
    const json = (await res.json()) as ApiResponse<JwtResponse>
    setSession({
      id: json.data.id,
      token: json.data.token,
      refreshToken: json.data.refreshToken,
    })
    return true
  } catch {
    setSession(null)
    return false
  }
}

async function doRequest<T>(path: string, opts: RequestOptions, allowRefresh: boolean): Promise<T> {
  const useAuth = opts.auth !== false
  const headers: Record<string, string> = {}

  const sentToken = useAuth ? getSession()?.token : undefined
  if (sentToken) headers.Authorization = `Bearer ${sentToken}`

  let body: BodyInit | undefined
  if (opts.body instanceof FormData) {
    // No Content-Type: the browser sets multipart/form-data with its boundary.
    body = opts.body
  } else if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(opts.body)
  }

  const res = await fetch(API_ROOT + path + buildQuery(opts.query), {
    method: opts.method ?? 'GET',
    headers,
    body,
  })

  // Access token likely expired — rotate once and replay the original request.
  if (res.status === 401 && allowRefresh && sentToken) {
    if (await tryRefresh(sentToken)) return doRequest<T>(path, opts, false)
  }

  if (!res.ok) throw await toApiError(res)
  return parseBody<T>(res)
}

export function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  return doRequest<T>(path, opts, true)
}
