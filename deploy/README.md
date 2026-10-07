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

The images come from GitHub Container Registry. For every commit on `master`, the
CI/CD pipeline ([`pipeline.yml`](../.github/workflows/pipeline.yml)) pushes
`ghcr.io/aryankanani1/shopping-cart-api` and `ghcr.io/aryankanani1/shopping-cart-web`,
tagged with the commit SHA, for `linux/amd64` and `linux/arm64`, once the commit has
passed the tests, the security scan and the quality gate. It then starts this setup
from those images as staging and runs the end-to-end tests against it; the images that
pass are also tagged `latest`. They are public, like the repository, so pulling them
needs no login. They contain the same code as the repository and no secrets: those
come from `.env` at run time.

## What you need

- A server with Docker Engine and the Compose plugin, ports **80** and **443**
  open, x86-64 or ARM64 (the images are built for both). 2 GB of memory is
  enough for the API (`-XX:MaxRAMPercentage=75.0`).
- A domain whose DNS record points at the server (Caddy needs it for the
  certificate).
- A managed MySQL 8 database, an empty schema, and a user with full rights on it.
  Flyway creates the tables on the first start.

## First deployment

1. Copy this directory to the server, e.g. `/opt/shop`: `docker-compose.yml`,
   `Caddyfile`, `deploy.sh` and `.env.example` (not `docker-compose.staging.yml`,
   which is the pipeline's staging setup).
2. Create `.env` from `.env.example` and fill it in:
   - `SITE_ADDRESS` — the domain;
   - `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` — the managed database, with
     `sslMode=VERIFY_IDENTITY` in the URL;
   - `JWT_SECRET` — `openssl rand -base64 32`;
   - `ADMIN_EMAIL` and `ADMIN_PASSWORD` (12+ characters) — the first admin.

   Keep it readable only by the deploying user: `chmod 600 .env`.
3. Start it:
   ```bash
   docker compose pull
   docker compose up -d
   docker compose logs -f api   # wait for "Started SpringSecurityDemoApplication"
   ```
4. Open `https://<your domain>`, sign in as the admin, and add the catalogue.
5. Remove `ADMIN_PASSWORD` from `.env` and run `docker compose up -d` again. The
   admin account keeps its password; change it from the account page.

## Updating

Once it is set up (next section), the pipeline deploys every commit on `master` that
passes staging, after you approve it in GitHub. By hand, on the server:

```bash
./deploy.sh <commit SHA>     # or: docker compose pull && docker compose up -d   (latest)
```

[`deploy.sh`](deploy.sh) sets `IMAGE_TAG` in `.env`, starts that build, waits up to
five minutes for the API to be ready, and if it isn't, puts the previous build back.
Running it with an earlier SHA rolls back, as long as no newer database migration has
run: Flyway migrations only move forward, so check before rolling back across one.

## Deploying from the pipeline

The pipeline's last stage, **Deploy production**, copies `docker-compose.yml`,
`Caddyfile` and `deploy.sh` to the server over SSH and runs `deploy.sh` with the
commit's SHA, then checks the site from outside. It runs only for the newest commit on
`master`, and only after a reviewer approves it in the `production` environment. Until
`PRODUCTION_URL` is set it is skipped. To set it up, after the first deployment above:

1. On the server, create a user for deployments that may run Docker, and give it the
   deployment directory (`/opt/shop` by default).
2. Create an SSH key pair for it (`ssh-keygen -t ed25519 -f shop-deploy -N ''`) and add
   `shop-deploy.pub` to that user's `~/.ssh/authorized_keys`.
3. In the repository's **Settings → Environments → production** (required reviewers:
   you; deployment branches: `master`), add the **secrets**
   - `PRODUCTION_SSH_KEY` — the private key, `shop-deploy`;
   - `PRODUCTION_SSH_KNOWN_HOSTS` — the server's host key line, from
     `ssh-keyscan <server>` checked against the server's own fingerprint;
   and the **variables** `PRODUCTION_SSH_TARGET` (`user@server`) and, if it isn't
   `/opt/shop`, `PRODUCTION_DIR`.
4. In **Settings → Secrets and variables → Actions → Variables**, add the repository
   variable `PRODUCTION_URL` (`https://<your domain>`). The next commit on `master`
   then waits for your approval to deploy.

The server's `.env` never leaves the server; the pipeline only changes `IMAGE_TAG`.

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
