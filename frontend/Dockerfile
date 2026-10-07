# ===========================================================================
# The storefront image: the production build served by Caddy. In production
# Caddy is also the front door: it terminates HTTPS and proxies /api to the API
# container. Its config (deploy/Caddyfile) is mounted at run time, so the same
# image serves every environment. See deploy/README.md.
# ===========================================================================

# --- Stage 1: build the static files ----------------------------------------
# Node 22, the version CI tests with. Both base images are pinned to a digest, which
# Dependabot updates when the tag moves. Runs on the building machine's own platform
# even when the image is built for another: the output is static files, the same
# on every platform, so it is built once instead of under emulation per target.
FROM --platform=$BUILDPLATFORM node:22-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402 AS build
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
FROM caddy:2-alpine@sha256:d8542f48d34a9cf4e4c11a478865229840e87e4c96ea3f439101f31a5d35f75f
COPY --from=build /app/dist /srv
