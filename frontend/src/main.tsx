import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'
import './styles/global.css'
import './styles/components.css'

const rootEl = document.getElementById('root')
if (!rootEl) throw new Error('Root element #root not found')

// One client for the whole app. Defaults tuned for a storefront: reads stay
// "fresh" for 30s (so navigating back to a page doesn't refetch immediately),
// one retry on failure, and no refetch on window focus (the catalogue doesn't
// change second-to-second — avoid surprise refetch storms when the user tabs back).
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})

createRoot(rootEl).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
