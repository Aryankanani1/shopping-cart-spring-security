# Deploying to one server

The whole shop runs on one Linux server with Docker, next to a managed MySQL:

```
browser ──HTTPS──▶ web (Caddy) ──/api──▶ api (Spring Boot) ──TLS──▶ managed MySQL 8
                     └─ storefront files
```

- **web** — Caddy with the built storefront. It gets and renews the HTTPS
  certificate for your domain, serves the storefront (with the `index.html`
  fallback client-side routing needs), and proxies `/api` to the API. Frontend
  and API share one origin, so no CORS is configured.
- **api** — the Spring Boot API with the `prod` profile. Only `web` can reach it;
  its actuator is never published.
- **MySQL** — a managed database (with its own backups), not a container here.

The images come from GitHub Container Registry. After CI passes on `master`,
[`publish-images.yml`](../.github/workflows/publish-images.yml) pushes
`ghcr.io/aryankanani1/shopping-cart-api` and `ghcr.io/aryankanani1/shopping-cart-web`,
tagged with the commit SHA and `latest`.

## What you need

- A server with Docker Engine and the Compose plugin, ports **80** and **443**
  open. 2 GB of memory is enough for the API (`-XX:MaxRAMPercentage=75.0`).
- A domain whose DNS record points at the server (Caddy needs it for the
  certificate).
- A managed MySQL 8 database, an empty schema, and a user with full rights on it.
  Flyway creates the tables on the first start.

## First deployment

1. Copy this directory to the server, e.g. `/opt/shop`: `docker-compose.yml`,
   `Caddyfile` and `.env.example`.
2. Create `.env` from `.env.example` and fill it in:
   - `SITE_ADDRESS` — the domain;
   - `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` — the managed database, with
     `sslMode=VERIFY_IDENTITY` in the URL;
   - `JWT_SECRET` — `openssl rand -base64 32`;
   - `ADMIN_EMAIL` and `ADMIN_PASSWORD` (12+ characters) — the first admin.

   Keep it readable only by the deploying user: `chmod 600 .env`.
3. New GHCR packages are private. On the server, log in with a GitHub token that
   has the `read:packages` scope:
   ```bash
   echo "$TOKEN" | docker login ghcr.io -u <github-user> --password-stdin
   ```
   (Or make both packages public in their GitHub settings and skip this.)
4. Start it:
   ```bash
   docker compose pull
   docker compose up -d
   docker compose logs -f api   # wait for "Started SpringSecurityDemoApplication"
   ```
5. Open `https://<your domain>`, sign in as the admin, and add the catalogue.
6. Remove `ADMIN_PASSWORD` from `.env` and run `docker compose up -d` again. The
   admin account keeps its password; change it from the account page.

## Updating

```bash
docker compose pull && docker compose up -d
```

To run a specific build, set `IMAGE_TAG` in `.env` to its commit SHA. Setting it
back to an earlier SHA rolls back, as long as no newer database migration has run.
Flyway migrations only move forward, so check before rolling back across one.

## Checking on it

- `docker compose ps` — both containers up; `web` is the only one with ports.
- `docker compose logs api` — application logs (rotated at 10 MB × 5 files).
- Readiness from the server: `docker compose exec web wget -qO- http://api:8080/actuator/health/readiness`.

## Limits of this setup

- **One API instance.** The auth rate limiter and the category cache live in
  memory, so a second replica would not share them. Scheduled jobs are already
  safe across instances (ShedLock), so the rest is ready for more.
- **Backups are the database's.** Turn on the managed MySQL's automated backups
  and point-in-time recovery; nothing here backs up data. Product images are
  stored in the database too.
- **Monitoring is local only.** The Prometheus/Grafana setup in the root
  `docker-compose.yml` is for development; production metrics need the scrape
  account (`PROMETHEUS_SCRAPE_*`) and a Prometheus that can reach `api`.
