# Shopping Cart — Spring Security

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen)
![Spring Security](https://img.shields.io/badge/Spring%20Security-JWT-green)
![Database](https://img.shields.io/badge/MySQL-Hibernate-blue)
![API Docs](https://img.shields.io/badge/API%20Docs-Swagger%20UI-85EA2D)
![Last commit](https://img.shields.io/github/last-commit/Aryankanani1/shopping-cart-spring-security)

An e-commerce REST API (**Spring Boot 4.1**, **Java 17**, MySQL + Flyway) with JWT
authentication, a catalog, cart, orders and simulated payments, and a **React +
Vite + TypeScript** storefront in [`frontend/`](frontend/).

The design is drawn, flow by flow, in two documents:

- [docs/security-design.md](docs/security-design.md): sign-in, tokens, the access
  map, ownership checks, rate limiting, webhooks.
- [docs/payment-design.md](docs/payment-design.md): payments, refunds,
  reconciliation, webhooks, Stripe, the transactional outbox and the checkout saga.

## Tech stack

| Concern | Choice |
|---|---|
| Framework | Spring Boot 4.1 (Web MVC, Data JPA, Security) |
| Database | MySQL, Hibernate (`ddl-auto: validate`), Flyway migrations |
| Auth | JWT signed RS256, Spring Security resource server, BCrypt |
| Resilience | Resilience4j retries and circuit breakers, ShedLock jobs |
| Observability | Actuator, Prometheus, Grafana |
| Frontend | React, Vite, TypeScript, React Query |
| Tests | JUnit, Mockito, Testcontainers, Vitest, Playwright, Schemathesis, k6 |

## Architecture

One Maven module per feature (`shop-<name>`, package
`com.aryan.spring_security_demo.<name>`), under a parent `pom.xml` that holds every
version:

```
shop-common/         config, aspects, error handler, response wrappers, the outbox
shop-identity/       users, auth, refresh tokens, security (filter chain, JWT, CORS, rate limiting)
shop-catalog/        products, categories, images
shop-cart/           carts
shop-order/          checkout, order lifecycle, history
shop-payment/        payments, refunds, Stripe, webhooks, the checkout saga
shop-notification/   in-app inbox
shop-wishlist/       wishlist, reminders, price/stock alerts (optional module)
shop-app/            the runnable app: main class, application*.yml, migrations, context tests
```

Dependencies point one way: every module may use `shop-common`; `shop-identity`
and `shop-catalog` ← `shop-cart` ← `shop-order` ← `shop-payment`; `shop-identity`
and `shop-catalog` ← `shop-notification` ← `shop-wishlist`. There is no app-wide
component scan: the main class `@Import`s one `XxxModule` per module.

## Getting started

Needs JDK 17+ and a MySQL server.

| Variable | Required? | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | optional | Default `prod`; use `dev` locally |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | prod | The database. In `dev` they fall back to `localhost:3306`, `root`, empty password |
| `JWT_SIGNING_KEY` | prod | RSA private key, PKCS#8 DER in Base64 (`dev` makes a throwaway one) |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | first start | Creates the shop's admin (`AdminBootstrap`) |
| `PAYMENT_WEBHOOK_SECRET` | prod | Signs the payment provider's webhooks |
| `APP_CORS_ALLOWED_ORIGINS` | if the storefront is on another origin | Allowed browser origins |

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw -pl shop-app -am spring-boot:run     # dev
cp .env.example .env && docker compose up --build                      # API + MySQL in Docker
```

The API is under `/api/v1`. Swagger UI is at `/swagger-ui.html` (off in `prod`).

## Default seed data

Only under the `dev` profile (`DevDataSeeder`); never created in production.

| Type | Credentials |
|---|---|
| Customers | `user1@gmail.com` … `user5@gmail.com` / `123456` |
| Admins | `admin1@gmail.com`, `admin2@gmail.com` / `123456` |

## Security model

Diagrams of every flow: [docs/security-design.md](docs/security-design.md). To
report a problem: [SECURITY.md](SECURITY.md).

- **Stateless JWTs** signed RS256; the public key is published at
  `/api/v1/.well-known/jwks.json`. Access tokens last 15 minutes; refresh tokens 7
  days, rotating, stored hashed.
- **Deny by default** in `ShopConfig`; admin rules there; record ownership checked
  in the services (`AuthUtils`).
- **Rate-limited `/auth/**`**, per client address, in memory, like the category
  cache: one instance, or a shared store behind a load balancer. Behind a proxy,
  set `SERVER_FORWARD_HEADERS_STRATEGY=native` so the limiter sees the client's
  address, not the proxy's.

## Payments

No real money moves: a simulated provider takes payments with test methods
(`test_visa`, `test_declined`, `test_late_charge`, ...), and Stripe's test mode can
take its place. Diagrams of every flow: [docs/payment-design.md](docs/payment-design.md).

| Endpoint | Purpose |
|---|---|
| `POST /orders/{orderId}/payments` | Pay, with an `Idempotency-Key` header |
| `GET /orders/{orderId}/payments` | The order's attempts |
| `POST /orders/{orderId}/refunds` | Admins: refund items or an amount |
| `GET /orders/{orderId}/refunds` | The order's refunds |
| `POST /payments/webhooks` | The provider's signed events |

| Guarantee | How | Design |
|---|---|---|
| Never charged twice | Idempotency keys, an order lock, one live payment per order | [§2](docs/payment-design.md#2-high-level-design) |
| No provider call inside a transaction | Record, call, record | [§2](docs/payment-design.md#2-high-level-design) |
| Lost outcomes are settled | Reconciliation and signed webhooks | [§5](docs/payment-design.md#5-phase-2-reconciliation), [§7](docs/payment-design.md#7-phase-3-webhooks) |
| Owed work is never lost | The transactional outbox | [§12](docs/payment-design.md#12-the-transactional-outbox) |
| A failed checkout is undone | The checkout saga's compensations | [§13](docs/payment-design.md#13-the-checkout-saga) |
| A charge after a failure goes back | Refunded in full | [§14](docs/payment-design.md#14-charges-after-a-failure) |
| Stripe outages are contained | Circuit breaker, retries, fallbacks | [§9](docs/payment-design.md#9-circuit-breaker-on-stripe), [§10](docs/payment-design.md#10-retries-and-fallbacks-with-resilience4j) |

### Stripe in test mode

1. Copy a test-mode secret key (`sk_test_...`) from Stripe's dashboard; the app
   refuses a live key.
2. In `.env` (git ignores it): `APP_PAYMENT_PROVIDER=stripe` and
   `STRIPE_SECRET_KEY=sk_test_...`.
3. Forward Stripe's webhooks with the Stripe CLI, and set `PAYMENT_WEBHOOK_SECRET`
   to the `whsec_...` that `stripe listen --print-secret` shows:
   ```bash
   stripe listen --events payment_intent.succeeded,payment_intent.payment_failed,refund.created,refund.updated \
     --forward-to localhost:8082/api/v1/payments/webhooks
   ```
4. Restart the API and pay with `test_visa`, `test_mastercard`, `test_declined` or
   `test_insufficient_funds`. The methods that simulate outages are fake-only.

Details: [payment-design.md, section 8](docs/payment-design.md#8-stripe-in-test-mode).

## Wishlist & notifications

| Endpoint | Purpose |
|---|---|
| `GET /wishlist`, `PUT`/`DELETE /wishlist/items/{productId}` | The caller's wishlist |
| `PUT`/`DELETE /wishlist/items/{productId}/reminder` | A dated reminder |
| `PUT /wishlist/items/{productId}/alerts` | Price-drop and back-in-stock alerts |
| `GET /notifications`, `GET /notifications/unread-count` | The in-app inbox |

`WishlistAlertJob` (ShedLock, every minute) sends each reminder and alert once.
`APP_MODULES_WISHLIST_ENABLED=false` switches the whole module off.

## Observability

| Endpoint | Who |
|---|---|
| `/actuator/health`, `/health/liveness`, `/health/readiness` | everyone |
| `/actuator/info`, `/actuator/metrics`, `/actuator/circuitbreakers`, `/actuator/retries` | admins |
| `/actuator/prometheus` | admins, or the Prometheus scrape account (`PROMETHEUS_SCRAPE_ENABLED`) |

Request metrics cover the business flows, e.g. orders placed:
`/actuator/metrics/http.server.requests?tag=uri:/api/v1/orders&tag=method:POST&tag=status:201`.

```bash
docker compose --profile monitoring up --build   # Prometheus :9090, Grafana :3000 (dashboard "Shopping Cart API")
```

## Build & test

```bash
./mvnw test                                        # every module (integration tests need Docker)
./mvnw test -DexcludedGroups=integration           # unit tests only
./mvnw test -pl shop-app -am -Dgroups=integration  # integration tests only
./mvnw verify                                      # + coverage, SpotBugs, PMD, CPD
cd frontend && npm test && npm run build           # Vitest, production build
```

[`docs/openapi.json`](docs/openapi.json) is the committed API contract;
`OpenApiContractTest` fails until it is regenerated after an API change
(`-Dopenapi.update=true`). Staging fuzzes the API against it with Schemathesis.

## Load testing

[`load/checkout.js`](load/checkout.js) (k6): up to 50 shoppers sign up, browse,
check out and pay, while the connection pool is sampled.

```bash
# the API (prod profile, rate limit raised, an admin to read the pool)
APP_RATELIMIT_CAPACITY=100000 ADMIN_EMAIL=loadadmin@load.test ADMIN_PASSWORD=<12+ characters> \
  SPRING_PROFILES_ACTIVE=prod ./mvnw -pl shop-app -am spring-boot:run -Dspring-boot.run.arguments=--server.port=8082
# k6 (APP_PAYMENT_FAKE_LATENCY=300ms on the API simulates a slow provider)
ADMIN_EMAIL=loadadmin@load.test ADMIN_PASSWORD=<the same> docker run --rm -e ADMIN_EMAIL -e ADMIN_PASSWORD \
  -v "$PWD/load:/load:ro" grafana/k6:2.3.0 run /load/checkout.js -e BASE_URL=http://host.docker.internal:8082
```

| On an Apple M1 laptop | Provider answers at once | Provider takes 300 ms |
|---|---|---|
| Requests (failed) | 19,811 (0%) | 17,023 (0%) |
| Throughput | 139 req/s, 27.5 purchases/s | 120 req/s, 24.5 purchases/s |
| p95 browse / check out / pay | 80 / 138 / 166 ms | 40 / 66 / 380 ms |

A slow provider doesn't drain the pool, because its call holds no connection. The
pool settings in `application.yml` (10 connections, a 5-second wait then `503`,
client-side statement caching) were chosen from runs of this test.

## CI/CD pipeline

[`.github/workflows/pipeline.yml`](.github/workflows/pipeline.yml):

```
commit ─▶ Build ─┬─▶ Unit tests ─▶ Integration tests ─┬─▶ Quality gate ─▶ Build artifact ─▶ Staging: deploy + E2E ─▶ Deploy production
                 ├─▶ Security scan ────────────────────┤                    (Trivy)           (Playwright, Schemathesis)   (after approval)
                 └─▶ Static analysis ──────────────────┘
```

Pull requests run every stage up to staging. On `master` the images that pass
staging become `latest` on GitHub Container Registry.

## Frontend

```bash
cd frontend && npm install && npm run dev   # http://localhost:5173, /api proxied to the API
```

Details: [frontend/README.md](frontend/README.md).

## Deploying and Docker

[`deploy/`](deploy/) runs the shop on one server with Docker Compose: Caddy in
front (HTTPS, the storefront, `/api` to the API) and a managed MySQL. Setup:
[deploy/README.md](deploy/README.md).

The images run as an unprivileged user, pin their base images to digests, and hold
no configuration or secrets: everything is injected at runtime. The root
`docker-compose.yml` is for local use only. In production the database is a
managed service, never a Compose volume: a volume lives and dies with one host,
with no backups, failover or point-in-time recovery.
