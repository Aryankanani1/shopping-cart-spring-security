import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ApiError, imageUrl, request } from './client'
import { getSession, setSession } from './tokenStore'

// A minimal stand-in for a fetch Response — enough for the client's use of
// ok/status/text()/json().
function resp(status: number, body?: unknown) {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: `status ${status}`,
    json: async () => body,
    text: async () => (body === undefined ? '' : JSON.stringify(body)),
  } as unknown as Response
}

const fetchMock = vi.fn()

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset()
  localStorage.clear()
  setSession(null)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('imageUrl', () => {
  it('builds the public image path from the id', () => {
    expect(imageUrl(42)).toBe('/api/v1/images/42')
  })
})

describe('request', () => {
  it('unwraps the { message, data } envelope', async () => {
    fetchMock.mockResolvedValueOnce(resp(200, { message: 'ok', data: { id: 1, name: 'Kettle' } }))

    const data = await request<{ id: number; name: string }>('/products/1', { auth: false })

    expect(data).toEqual({ id: 1, name: 'Kettle' })
    expect(fetchMock).toHaveBeenCalledOnce()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/products/1')
    expect(init.method).toBe('GET')
  })

  it('drops nullish/empty query params and encodes the rest', async () => {
    fetchMock.mockResolvedValueOnce(resp(200, { message: 'ok', data: [] }))

    await request('/products', {
      auth: false,
      query: { name: 'tea pot', brand: '', page: 0, category: undefined },
    })

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/products?name=tea%20pot&page=0')
  })

  it('attaches the bearer token when authenticated', async () => {
    setSession({ id: 7, token: 'access-1', refreshToken: 'refresh-1' })
    fetchMock.mockResolvedValueOnce(resp(200, { message: 'ok', data: null }))

    await request('/users/7')

    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer access-1')
  })

  it('sends a JSON body with the correct content-type', async () => {
    fetchMock.mockResolvedValueOnce(resp(200, { message: 'ok', data: { id: 1, token: 't', refreshToken: 'r' } }))

    await request('/auth/login', {
      method: 'POST',
      auth: false,
      body: { email: 'a@b.com', password: 'x' },
    })

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/auth/login')
    expect(init.method).toBe('POST')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(JSON.parse(init.body)).toEqual({ email: 'a@b.com', password: 'x' })
  })

  it('sends a FormData body as-is, leaving the multipart content-type to the browser', async () => {
    fetchMock.mockResolvedValueOnce(resp(201, { message: 'ok', data: [] }))
    const form = new FormData()
    form.append('files', new File(['x'], 'a.png', { type: 'image/png' }))

    await request('/images', { method: 'POST', body: form })

    const [, init] = fetchMock.mock.calls[0]
    expect(init.body).toBe(form)
    expect(init.headers['Content-Type']).toBeUndefined()
  })

  it('returns undefined for 204 No Content', async () => {
    fetchMock.mockResolvedValueOnce(resp(204))
    const data = await request('/carts/1/items', { method: 'DELETE' })
    expect(data).toBeUndefined()
  })

  it('throws ApiError carrying the problem+json detail and field errors', async () => {
    fetchMock.mockResolvedValueOnce(
      resp(400, {
        title: 'Validation failed',
        detail: 'One or more fields are invalid',
        status: 400,
        errors: { email: 'must be a valid address' },
      }),
    )

    const err = (await request('/users', { method: 'POST', auth: false, body: {} }).catch(
      (e) => e,
    )) as ApiError

    expect(err).toBeInstanceOf(ApiError)
    expect(err.status).toBe(400)
    expect(err.message).toBe('One or more fields are invalid')
    expect(err.fieldErrors).toEqual({ email: 'must be a valid address' })
  })
})

describe('401 -> refresh -> retry', () => {
  it('rotates the token once and replays the original request', async () => {
    setSession({ id: 7, token: 'stale', refreshToken: 'refresh-1' })
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/auth/refresh')) {
        return Promise.resolve(
          resp(200, { message: 'refreshed', data: { id: 7, token: 'fresh', refreshToken: 'refresh-2' } }),
        )
      }
      // Data endpoint: 401 while the stale token is presented, 200 once rotated.
      return Promise.resolve(
        getSession()?.token === 'fresh'
          ? resp(200, { message: 'ok', data: { id: 99 } })
          : resp(401, { title: 'Authentication failed', detail: 'Invalid or expired token' }),
      )
    })

    const data = await request<{ id: number }>('/orders/99')

    expect(data).toEqual({ id: 99 })
    const refreshCalls = fetchMock.mock.calls.filter((call) => String(call[0]).includes('/auth/refresh'))
    expect(refreshCalls).toHaveLength(1)
    expect(getSession()?.token).toBe('fresh')
    expect(getSession()?.refreshToken).toBe('refresh-2')
  })

  it('collapses concurrent 401s into a single refresh (single-flight)', async () => {
    setSession({ id: 7, token: 'stale', refreshToken: 'refresh-1' })
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/auth/refresh')) {
        return Promise.resolve(
          resp(200, { message: 'refreshed', data: { id: 7, token: 'fresh', refreshToken: 'refresh-2' } }),
        )
      }
      return Promise.resolve(
        getSession()?.token === 'fresh' ? resp(200, { message: 'ok', data: true }) : resp(401, { detail: 'expired' }),
      )
    })

    const [a, b] = await Promise.all([request('/orders/1'), request('/orders/2')])

    expect(a).toBe(true)
    expect(b).toBe(true)
    const refreshCalls = fetchMock.mock.calls.filter((call) => String(call[0]).includes('/auth/refresh'))
    expect(refreshCalls).toHaveLength(1)
  })

  it('clears the session and throws when the refresh itself fails', async () => {
    setSession({ id: 7, token: 'stale', refreshToken: 'bad' })
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(url.includes('/auth/refresh') ? resp(401, { detail: 'nope' }) : resp(401, { detail: 'expired' })),
    )

    const err = await request('/orders/1').catch((e) => e)

    expect(err).toBeInstanceOf(ApiError)
    expect(getSession()).toBeNull()
  })
})
