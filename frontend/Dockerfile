# ===========================================================================
# The storefront image: the production build served by Caddy. In production
# Caddy is also the front door: it terminates HTTPS and proxies /api to the API
# container. Its config (deploy/Caddyfile) is mounted at run time, so the same
# image serves every environment. See deploy/README.md.
# ===========================================================================

# --- Stage 1: build the static files ----------------------------------------
# Node 22, the version CI tests with. Every base image is pinned to a digest, which
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

# --- Stage 2: Caddy, built with fixed dependencies (temporary) ---------------
# The Caddy in caddy:2-alpine (v2.11.9) was built with golang.org/x/net v0.60.0,
# which has a HIGH CVE with a fix released:
#   CVE-2026-78661   golang.org/x/net/html, fixed in x/net v0.62.0
# No Caddy release includes the fix yet, so this builds the same Caddy version
# the same way (standard modules, the official build tags and flags) with x/net
# v0.62.0. Caddy v2.11.9 itself requires go-jose v4.1.6, which fixes
# CVE-2026-116587 (HIGH) in the v4.1.5 of v2.11.7, the version built here
# before. Every other dependency stays at the version Caddy v2.11.9 requires,
# except the four x/net v0.62.0 needs one release newer (x/crypto, x/sys, x/term,
# x/text), which Go's module resolution picks.
# It is built with Go 1.27 for the Go standard library CVEs this stage was first
# added for (CVE-2026-78667, CVE-2026-97031, CVE-2026-78669: fixed in Go 1.26.9;
# CVE-2026-111943: fixed in Go 1.26.10 and 1.27.3).
# Remove this stage, and the COPY and RUN after the last FROM, once caddy:2-alpine
# ships a Caddy built with Go 1.26.10 or later (or 1.27.3 or later), x/net v0.62.0
# or later and go-jose v4.1.6 or later (a release after v2.11.9): `caddy
# build-info` in the image shows all three.
# Cross-compiled on the building machine's platform, so no emulation is needed.
FROM --platform=$BUILDPLATFORM golang:1.27-alpine@sha256:b8c9b82affbbb82a020bbcf998dad2ba9516944ac7e883524642a481a9505e9c AS caddy
ARG TARGETOS
ARG TARGETARCH
WORKDIR /src
# Caddy's own cmd/caddy/main.go: the standard modules, nothing added.
RUN printf '%s\n' \
        'package main' \
        'import (' \
        '    caddycmd "github.com/caddyserver/caddy/v2/cmd"' \
        '    _ "github.com/caddyserver/caddy/v2/modules/standard"' \
        ')' \
        'func main() { caddycmd.Main() }' > main.go \
    && go mod init caddy \
    && go get github.com/caddyserver/caddy/v2@v2.11.9 golang.org/x/net@v0.62.0 \
    && go mod tidy \
    && CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH \
       go build -trimpath -tags nobadger,nomysql,nopgx -ldflags '-s -w' -o /out/caddy .

# --- Stage 3: Caddy with the built files ------------------------------------
FROM caddy:2-alpine@sha256:003a97808e781aa9f7e75cc2f43d34d461099031faf5e20faefb9f61126836f0
COPY --from=caddy /out/caddy /usr/bin/caddy
# As the official image does: lets Caddy bind ports 80 and 443 without root.
RUN setcap cap_net_bind_service=+ep /usr/bin/caddy && caddy version
COPY --from=build /app/dist /srv
