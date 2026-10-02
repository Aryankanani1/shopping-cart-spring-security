import type { ReactElement } from 'react'
import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'

/** Prints the router's current location, so a test can see where a page navigated to. */
function CurrentLocation() {
  const location = useLocation()
  return (
    <p data-testid="location">
      {location.pathname}
      {location.search}
    </p>
  )
}

interface Options {
  /** Route pattern the page is mounted at, e.g. "/orders/:id". */
  path?: string
  /** URL to open, e.g. "/orders/55" or "/products?name=lamp". Defaults to `path`. */
  url?: string
  /** Router state to open the URL with (e.g. `{ from: '/checkout' }`). */
  state?: unknown
}

/**
 * Render one page the way the router would, inside a fresh React Query client
 * with retries off. Every other route shows the current location, so
 * `screen.getByTestId('location')` tells you where a navigation went.
 */
export function renderPage(page: ReactElement, { path = '/', url = path, state }: Options = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const [pathname, search] = url.split('?')
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[{ pathname, search: search ? `?${search}` : '', state }]}>
        <Routes>
          <Route
            path={path}
            element={
              <>
                {page}
                <CurrentLocation />
              </>
            }
          />
          <Route path="*" element={<CurrentLocation />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return { ...utils, queryClient }
}
