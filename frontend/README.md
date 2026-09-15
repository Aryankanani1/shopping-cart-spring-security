# Meridian — storefront

A customer-facing single-page storefront for the Spring Security shopping-cart
API, built with **React + Vite + TypeScript**. It consumes the existing REST
endpoints under `/api/v1` — no backend changes required for local development.

## What's covered

- **Browse** — landing page, paginated + filterable catalogue (`/products`),
  product detail with image gallery.
- **Auth** — register, login, logout. JWT access token + **automatic refresh**
  (rotating refresh token) handled transparently in the API client.
- **Cart** — add to bag, change quantity, remove, clear; live bag count in the
  header.
- **Checkout** — turn the cart into an order, then land on an order confirmation.
- **Orders** — order history with keyset "load more", plus order detail.

## Prerequisites

- **Node.js 18+**
- The **API running on `http://localhost:8080`** with some seeded data. From the
  repo root:
  ```bash
  JWT_SECRET=$(openssl rand -base64 32) ./mvnw spring-boot:run
  ```
  The `dev` profile seeds demo accounts (e.g. `user1@gmail.com` / `123456`) and
  catalogue categories. Add a few products/images as an admin to see the grid
  fill out.

## Run

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```

The dev server **proxies `/api` to `http://localhost:8080`** (see
`vite.config.ts`), so the browser talks to a single origin and **no CORS
configuration is needed on the backend**.

### Scripts

| Script              | Purpose                                  |
|---------------------|------------------------------------------|
| `npm run dev`       | Vite dev server with API proxy + HMR     |
| `npm run build`     | Type-check (`tsc`) then production build |
| `npm run preview`   | Serve the production build locally       |
| `npm run typecheck` | Type-check only                          |

## Configuration

Copy `.env.example` to `.env` if you need to override the API origin:

```env
# Empty in dev — the Vite proxy forwards /api to :8080 (same origin).
# In prod, point at the real API origin, e.g. https://api.example.com
VITE_API_BASE_URL=
```

## Testing

Unit tests use **Vitest** + **React Testing Library** (jsdom). They are *automated*
— `npm test` runs them headless and reports pass/fail (no browser, no clicking).

```bash
npm test          # run once
npm run test:watch
```

**27 tests across 6 files** cover the hand-written logic: the API client (envelope
unwrapping, problem+json → `ApiError`, and the 401 → refresh → retry / single-flight
path), the token store, `CartContext` (cart resolution + item-count), the
`RequireAuth` redirect, `ProductCard`, and the formatting helpers.

> **iCloud caveat:** this repo lives in the iCloud-synced `~/Desktop`, and Vitest
> hangs at config-load/dep-scan here — esbuild/Vite stall re-materializing evicted
> `node_modules` files from iCloud (same root cause as the known `git push` hang).
> The suite was verified green on a local copy. To run it, either move the repo out
> of iCloud (recommended — also fixes git), or use a throwaway local copy:
>
> ```bash
> rsync -a --exclude=node_modules --exclude=dist ./ /tmp/frontend-test/
> npm --prefix /tmp/frontend-test install && npm --prefix /tmp/frontend-test test
> ```

## Project structure

```
src/
  api/         Typed client + per-domain modules (auth, products, cart, orders…)
               - client.ts     fetch wrapper: {message,data} unwrap, problem+json
                               errors, single-flight 401 -> refresh -> retry
               - types.ts      TS mirrors of the backend DTOs / envelopes
               - tokenStore.ts localStorage-backed session, source of truth
  context/     AuthContext (session) + CartContext (cart + header badge)
  components/  Layout, Header, Footer, ProductCard, RequireAuth, stepper, ui atoms
  pages/       Home, Products, ProductDetail, Login, Register, Cart, Orders, OrderDetail
  hooks/       useAsync (load/loading/error), errMessage
  lib/         formatting helpers (money, date, class names, CSS vars)
  styles/      global.css (design tokens + primitives) + components.css
```

## Notes on the API contract

- **Success envelope** — every endpoint returns `{ message, data }`; the client
  unwraps and hands callers the `data`. Errors are RFC 7807 `problem+json`;
  `ApiError` surfaces `detail`/`title` and the per-field `errors` map (used for
  form validation on register).
- **The cart id isn't known up front** — `POST /cartItems` takes only
  `productId`/`quantity` and resolves the current user's cart server-side. The
  cart (and its id, needed for quantity/remove/clear) is read back from
  `GET /users/{id}` → `cart`. `CartContext` centralises this.
- **Product images** are served at `GET /api/v1/images/{id}` (public). The client
  builds `src` from the image id via `imageUrl()`. Note the backend's stored
  `downloadUrl` field currently points at a non-matching path
  (`/images/image/download/{id}`) and is intentionally **not** used here — worth
  fixing backend-side.
- **Sorting** is allowlisted to `id, name, price, brand`; the sort dropdown only
  offers those.

## Production

`npm run build` emits static assets to `dist/`. Serve them behind the same origin
as the API (so `/api` resolves), or set `VITE_API_BASE_URL` to the API origin and
enable CORS there — the backend does not currently send CORS headers.
