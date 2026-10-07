# ===========================================================================
# The storefront image: the production build served by Caddy. In production
# Caddy is also the front door: it terminates HTTPS and proxies /api to the API
# container. Its config (deploy/Caddyfile) is mounted at run time, so the same
# image serves every environment. See deploy/README.md.
# ===========================================================================

# --- Stage 1: build the static files ----------------------------------------
# Node 22, the version CI tests with.
FROM node:22-alpine AS build
WORKDIR /app

# Dependencies first, so this layer is reused until the lockfile changes.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# Served from the same host as the API (Caddy proxies /api), so API calls stay
# relative and no CORS is involved.
ENV VITE_API_BASE_URL=
RUN npm run build

# --- Stage 2: Caddy with the built files ----------------------------------
FROM caddy:2-alpine
COPY --from=build /app/dist /srv
