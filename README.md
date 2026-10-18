# Shopping Cart — Spring Security

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen)
![Spring Security](https://img.shields.io/badge/Spring%20Security-JWT-green)
![Database](https://img.shields.io/badge/MySQL-Hibernate-blue)
![REST](https://img.shields.io/badge/REST-Maturity%20Level%202-blueviolet)
![API Docs](https://img.shields.io/badge/API%20Docs-Swagger%20UI-85EA2D)
![Build](https://img.shields.io/badge/build-Maven-red)
![Last commit](https://img.shields.io/github/last-commit/Aryankanani1/shopping-cart-spring-security)

An e-commerce REST API built with **Spring Boot 4.1** and **Java 17**, featuring
JWT-based stateless authentication, role-based authorization, a product catalog,
cart, and order flow. Persistence is tuned for Hibernate best practices
(batching, optimistic locking, `open-in-view=false`), and
interactive API documentation is served by **Swagger UI** (springdoc-openapi).

A **React + Vite + TypeScript** customer storefront lives in
[`frontend/`](frontend/) and consumes this API — see [Frontend](#frontend) to run it.

## Tech stack

| Concern        | Choice                                             |
|----------------|----------------------------------------------------|
| Framework      | Spring Boot 4.1.0 (Web MVC, Data JPA, Security)    |
| Language       | Java 17                                            |
| Database       | MySQL (`mysql-connector-j`), Hibernate             |
| Auth           | JWT (jjwt 0.12.5), stateless, BCrypt               |
| Mapping        | ModelMapper 3.2.4                                  |
| API docs       | springdoc-openapi 3.1.0 (OpenAPI 3 + Swagger UI)   |
| Caching        | Spring Cache (`ConcurrentMapCacheManager`)         |
| Aspects (AOP)  | Spring AOP + AspectJ (`aspectjweaver`) — service-layer logging |
| Migrations     | Flyway (`spring-boot-starter-flyway` + `flyway-mysql`) |
| Observability  | Spring Boot Actuator (health probes, metrics), Prometheus, Grafana |
| Boilerplate    | Lombok                                             |
| Build          | Maven wrapper (`./mvnw`)                           |
| Container      | Multi-stage Docker (non-root, JRE-only runtime)    |

## Getting started

### Prerequisites
- JDK 17+
- MySQL running and reachable

### Configure

Configuration follows a **commit-safe-defaults, override-per-environment** model
— no config file needs to be copied or created to run locally:

- `application.yml` — committed **base** with shared, safe defaults.
- `application-dev.yml` / `application-prod.yml` — carry only what differs per
  environment (SQL logging, log levels, and prod-only hardening: API docs off,
  actuator health details hidden).
- **Secrets are never committed** — they are read from environment variables and
  override the committed defaults via Spring's property precedence.

The active profile defaults to `prod`; select dev for local runs with
`SPRING_PROFILES_ACTIVE=dev`. Prod is the fallback on purpose: dev seeds admin
accounts with a known password, so a deploy that forgets to choose a profile must
not end up there.

Environment variables:

| Variable          | Required?            | Purpose                                            |
|-------------------|----------------------|----------------------------------------------------|
| `JWT_SECRET`      | **always**           | Base64 key, ≥256 bits — `openssl rand -base64 32`  |
| `DB_URL`          | prod (dev: localhost)| JDBC URL, e.g. `jdbc:mysql://localhost:3306/shop`  |
| `DB_USERNAME`     | prod (dev: `root`)   | Database user                                      |
| `DB_PASSWORD`     | prod (dev: empty)    | Database password                                  |
| `SPRING_PROFILES_ACTIVE` | optional      | Active profile, default `prod` (`dev` locally)     |
| `JWT_EXPIRATION_MS` | optional           | Access-token lifetime, default `900000` (15m)      |
| `JWT_REFRESH_EXPIRATION_MS` | optional   | Refresh-token lifetime, default `604800000` (7d)   |
| `APP_RATELIMIT_EVICTION_CRON` | optional | Sweep of replenished rate-limit buckets, default hourly |
| `APP_WISHLIST_ALERT_CRON` | optional       | Wishlist reminder/alert scan, default every minute (`-` disables) |
| `APP_MODULES_WISHLIST_ENABLED` | optional  | `false` switches the whole wishlist module off (default `true`) |
| `ADMIN_EMAIL`     | first deploy         | Creates this account as an admin at startup, or gives an existing one the admin role (`AdminBootstrap`) |
| `ADMIN_PASSWORD`  | with `ADMIN_EMAIL`   | 12-72 characters; only used to create the account, so remove it once the admin exists |
| `APP_CORS_ALLOWED_ORIGINS` | if the frontend is on another origin | Comma-separated exact origins allowed to call the API, e.g. `https://shop.example.com` (default: none, same-origin only) |

In **dev** the datasource falls back to a local MySQL (`localhost:3306`,
`root`, empty password) so the app boots out of the box; any value can still be
overridden by exporting the matching env var.

### Run

```bash
# dev
SPRING_PROFILES_ACTIVE=dev JWT_SECRET=$(openssl rand -base64 32) ./mvnw -pl shop-app -am spring-boot:run

# prod (the default profile)
DB_URL=... DB_USERNAME=... DB_PASSWORD=... \
  JWT_SECRET=... ./mvnw -pl shop-app -am spring-boot:run
```

`-pl shop-app -am` runs the application module and builds the modules it uses
first (see [Architecture](#architecture)).

The API is served under the `api.prefix` (default `/api/v1`).

## Architecture

The code is organised **by feature module**, not by layer: each module holds its
own controllers, services, repositories, entities, DTOs and exceptions. Each
feature module is a Maven module of its own (`shop-<name>`, package
`com.aryan.spring_security_demo.<name>`), under a parent `pom.xml` that holds the
module list and every dependency version:

```
pom.xml              Parent (packaging pom): Spring Boot parent, versions and BOMs, modules
shop-common/         Shared infrastructure: config (cache, scheduling, AOP, clock, OpenAPI,
                     ModelMapper), aop/ (logging + security-audit aspects), exception/ (base
                     exception types + global handler), web/ (ApiResponse, paging envelopes),
                     validation/, bootstrap/ (startup diagnostics)
shop-identity/       Users, roles, auth (login/refresh/logout/password), refresh tokens, role,
                     dev-user and first-admin seeding; security/ (filter chain, JWT, CORS, rate
                     limiting, ownership checks)
shop-catalog/        Products, categories, images, the category cache, catalog seeding + warm-up
shop-cart/           Carts and cart items
shop-order/          Checkout, order lifecycle, keyset-paged history
shop-payment/        Paying for orders: idempotent attempts, a simulated provider, retries
shop-notification/   In-app inbox
shop-wishlist/       Wishlist, dated reminders, price/stock alert job   (optional module)
shop-app/            The runnable application: SpringSecurityDemoApplication, application*.yml,
                     db/migration/ (Flyway: V1__baseline.sql, …), and the tests that need the
                     whole application; builds the jar and the Docker image
```

Dependencies point one way, with no cycles, and Maven enforces it, since a module
sees only the modules it declares: every module may use `shop-common`;
`shop-identity` and `shop-catalog` ← `shop-cart` ← `shop-order` ← `shop-payment`;
`shop-identity` and `shop-catalog` ← `shop-notification` ← `shop-wishlist`. Modules
further down react to changes higher up through events (`UserDeletingEvent`,
`ProductDeletingEvent`, `OrderCancellingEvent`), never by importing the module
that depends on them.

### Modular component scanning

There is **no app-wide component scan**. `SpringSecurityDemoApplication` uses
`@SpringBootConfiguration` + `@EnableAutoConfiguration` (not
`@SpringBootApplication`) and lists its modules with `@Import`:

```java
@Import({ CommonModule.class, IdentityModule.class, CatalogModule.class, CartModule.class,
          OrderModule.class, NotificationModule.class, WishlistModule.class })
```

Each `XxxModule` class is marked `@ModuleConfiguration`, which scans **only that
module's package** (components and `@ConfigurationProperties`). So the
application's contents are stated in one place rather than implied by where
files happen to sit, and a module can be conditional as a unit:

- **Optional module** — `WishlistModule` is `@ConditionalOnBooleanProperty`
  on `app.modules.wishlist.enabled`. Setting `APP_MODULES_WISHLIST_ENABLED=false`
  removes its endpoints, services and scheduled alert job, with no code change.
  Its tables and repository stay, so switching it back on resumes where it left off.
- **Slice tests still work** — module scans apply Boot's `TypeExcludeFilter`
  (as `@SpringBootApplication` does), which is how `@WebMvcTest` narrows scanning
  to the web layer.
- **Persistence stays app-wide** — `@EnableAutoConfiguration` registers the
  root package, so entities and repositories are found in every module. Declaring
  `@EnableJpaRepositories` per module would break the database-less test slices.
- **Guard rail** — a class in a package no module covers is never registered.
  `ModuleCompositionTest` fails if one appears, and proves a stray `@Component`
  outside every module is not picked up.

### Configuration & profiles
- **Typed, validated config**: `app.startup.*` and `auth.token.*` bind to
  `@ConfigurationProperties` beans (`@Validated`) rather than scattered `@Value` —
  invalid config fails fast at startup.
- **Profiles decide beans, not `if` checks**: `DevDataSeeder` is `@Profile("dev")`,
  so test users/admins exist only in dev; production never creates them. Roles
  (needed everywhere) are seeded unconditionally by `DataInitializer`.
- **Flyway owns the schema** in every environment: versioned SQL migrations under
  `shop-app/src/main/resources/db/migration` build the DDL, and Hibernate runs `ddl-auto: validate`
  in **both dev and prod** — it only checks the entities match the schema, never
  mutates it. The integration tests do the same against MySQL 8.4 in a container
  (Testcontainers), so a migration that doesn't match the entities fails them.

### Database migrations

Flyway applies the migrations at startup, before Hibernate validates. The baseline
`V1__baseline.sql` was generated from the entities via Hibernate's `MySQLDialect`
export, so `ddl-auto: validate` accepts it verbatim. All later changes go in new
`V2__…`, `V3__…` files — never edit an applied migration. `baseline-on-migrate`
is enabled so Flyway can adopt a pre-existing database (e.g. a dev schema built by
an earlier `ddl-auto: update`) instead of failing on a non-empty schema.

Ids are MySQL `AUTO_INCREMENT` columns (`GenerationType.IDENTITY`). V1 created
`*_seq` tables to emulate sequences, but Hibernate reads those on a second pooled
connection mid-transaction, which let enough simultaneous requests stall the whole
pool; `V9__auto_increment_ids.sql` switched every id column and dropped the tables.

> **Boot 4 note**: the migrations only run because `spring-boot-starter-flyway` is
> on the classpath. Boot 4 moved the autoconfiguration into a per-module jar, so
> raw `flyway-core` alone leaves Flyway inert and prod dies on `validate`.

### Observability

Spring Boot Actuator exposes only `health`, `info`, `metrics` and `prometheus` over HTTP.

| Endpoint | Who | Shows |
|---|---|---|
| `/actuator/health`, `/health/liveness`, `/health/readiness` | everyone | `UP`/`DOWN` — for load balancers and Kubernetes probes |
| `/actuator/health` details (database, disk space) | admins | per-component status (dev only; prod's `show-details` is `never`) |
| `/actuator/info` | admins | app version and build time (from the Maven `build-info` goal), Java version, process (pid, CPUs, memory) |
| `/actuator/metrics`, `/actuator/metrics/{name}` | admins | JVM, HTTP, datasource and cache metrics |
| `/actuator/prometheus` | admins, or the Prometheus scrape account when it's on | the same metrics in Prometheus format |

How it's locked down:
- **Admin-only by endpoint, not by path.** `ShopConfig` matches the actuator with
  `EndpointRequest` (health public, every other endpoint `ROLE_ADMIN`), so the rules
  still hold if the base path or the management port changes. Health details are
  shown only to admins (`management.endpoint.health.roles`).
- **Everything else is switched off.** `management.endpoints.access.default: none`
  turns off every endpoint not explicitly enabled, and `max-permitted: read-only`
  rules out write operations. Widening the exposure list (even to `*`) can't bring
  up `env`, `heapdump`, `threaddump`, `loggers` or `shutdown`.
- **Optional: a separate port.** Set `MANAGEMENT_SERVER_PORT` to serve the actuator
  on its own port that you don't route publicly. The same rules (JWT included)
  apply there. Point the probes at that port.

Tests: `ActuatorAccessTest`, `ActuatorLockdownTest`, `ActuatorBasePathTest`,
`ActuatorManagementPortTest`, `PrometheusScrapeTest`.
Request metrics already cover the business flows — e.g. orders placed:
`/actuator/metrics/http.server.requests?tag=uri:/api/v1/orders&tag=method:POST&tag=status:201`.

### Prometheus

Metrics are also served in Prometheus format at `/actuator/prometheus`. Every
series carries an `application="spring_security_demo"` label, and HTTP requests
have latency buckets, so you can query percentiles with `histogram_quantile`.

Prometheus can't log in for a JWT, so it scrapes with HTTP Basic using a
dedicated **scrape account** (`PrometheusScrapeSecurityConfig`):

| Setting | Default | |
|---|---|---|
| `PROMETHEUS_SCRAPE_ENABLED` | `false` | Off: `/actuator/prometheus` is admin-only like the rest of the actuator |
| `PROMETHEUS_SCRAPE_USERNAME` | `prometheus` | |
| `PROMETHEUS_SCRAPE_PASSWORD` | — | Required when enabled, 16-72 characters (`openssl rand -base64 24`); startup fails otherwise |

The account can read `/actuator/prometheus` and nothing else, and no other
credentials work on that endpoint while it's on (an admin's JWT included). Its
password is held only as a BCrypt hash.

**Run Prometheus and Grafana locally** with the compose `monitoring` profile. In
`.env`, set `PROMETHEUS_SCRAPE_ENABLED=true`, a `PROMETHEUS_SCRAPE_PASSWORD` and a
`GRAFANA_ADMIN_PASSWORD`, then:

```bash
docker compose --profile monitoring up --build
# Prometheus  http://localhost:9090  (PROMETHEUS_PORT)
# Grafana     http://localhost:3000  (GRAFANA_PORT) — user "admin", GRAFANA_ADMIN_PASSWORD
```

Both UIs listen on localhost only. Passwords reach the containers as compose
secret files (Prometheus' `basic_auth` takes a `password_file`, not an
environment variable). Grafana reads its admin password only when it first
creates its database; to change it later, use the UI or remove the
`shopping-cart-grafana-data` volume. The scrape job is in
`monitoring/prometheus.yml`; copy it into your own Prometheus config for a real
deployment.

Grafana comes provisioned with the Prometheus datasource and a **Shopping Cart
API** dashboard (folder *Shopping Cart*), defined in
`monitoring/grafana/dashboards/shopping-cart-api.json`:

| Row | Panels |
|---|---|
| Overview | uptime, requests/s, share of 5xx responses, orders placed in the last hour |
| Traffic | request rate and p95 latency per endpoint |
| Outcomes | responses by status, logins by outcome (200 / 401 / 429 rate-limited) |
| Resources | JVM heap, database pool (active / idle / waiting connections) |

The dashboard is read-only in the UI; change the JSON file to change it.
Example queries:

```promql
# Request rate per endpoint
sum by (uri) (rate(http_server_requests_seconds_count{job="shopping-cart-api"}[5m]))
# 95th-percentile latency per endpoint
histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{job="shopping-cart-api"}[5m])))
# Orders placed per minute
sum(rate(http_server_requests_seconds_count{uri="/api/v1/orders", method="POST", status="201"}[5m])) * 60
```

### Security model
- **Stateless access JWT**: `AuthTokenFilter` runs before `UsernamePasswordAuthenticationFilter`
  and authenticates each request from the bearer token — no server-side session lookup, so reads scale.
- **Deny-by-default authorization** (`ShopConfig`): only listed paths are public
  (login, self-registration, GET catalog, docs); catalog writes are `ROLE_ADMIN`;
  everything else requires authentication. **Object-level ownership** (a user may
  only touch their own cart/order/account) is enforced in the service layer via
  `AuthUtils.requireSelfOrAdmin` — role rules at the edge, ownership at the load.
- **Short access token + revocable refresh token**: the access token is short-lived
  (15 min) to shrink the exposure window of a leak; clients keep sessions alive by
  exchanging a longer-lived (7 day) refresh token at `POST /api/v1/auth/refresh`.
  Refresh tokens are persisted **hashed** and are **rotating** (each refresh revokes
  the old one and issues a new one, so replay of a spent token is detected).
  `POST /api/v1/auth/logout` revokes the refresh token, ending the session server-side.
- **Password change ends every session**: `PUT /api/v1/auth/password` (authenticated)
  re-verifies the current password (a valid token alone isn't enough), stores the new
  BCrypt hash, and deletes all of the user's refresh tokens in the same transaction,
  so other devices are signed out. It then returns a fresh token pair, so the caller
  stays signed in. The old tokens are *deleted* rather than revoked: replaying a
  revoked token trips reuse detection, which would also kill the caller's new token.
  Other devices' access tokens still lapse within their 15-minute lifetime.
- **Account deletion keeps the orders**: `DELETE /api/v1/users/{id}` (the owner or an
  admin) ends every session and removes the cart, wishlist and notifications. Orders
  not yet shipped are cancelled, their stock returned and paid ones refunded (one
  being paid for blocks the deletion until its payment settles); every order is then kept for the
  shop's records with no customer (`user_id` set to null), and the admin order list
  shows it as a deleted account. The shipping snapshot (name, address) stays on it.
- **Rate-limited auth endpoints**: `RateLimitFilter` sits ahead of authentication in
  the chain and throttles `/auth/**` (login, refresh, logout, password change) per client IP with an
  in-memory token bucket (`RateLimitService`) — `capacity` requests may burst, then
  the bucket refills to full over `refill-period`. Over-limit callers get `429 Too
  Many Requests` (RFC 7807 body + `Retry-After`) *before* any credential/token work,
  which blunts brute-force and credential stuffing. Tight defaults (5 req / 1 min)
  because only the auth endpoints are guarded. Single-instance by design (like the
  in-memory cache); behind a load balancer, back it with a shared store — the call
  site doesn't change.
  Behind a reverse proxy or load balancer, set `SERVER_FORWARD_HEADERS_STRATEGY=native`,
  or the limiter sees only the proxy's address and every client shares one bucket.
  Tomcat then takes the client address from `X-Forwarded-For`, but only when the
  request comes from a private-network address; if the proxy is elsewhere, list it
  in `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` (a regex). It is off by default
  because, with no proxy in front, a client on a private network could send its
  own `X-Forwarded-For` and get a fresh bucket on every request.
- Roles: `ROLE_ADMIN`, `ROLE_CUSTOMER`. Every account created through `POST /users`
  is a customer.

## API

The API follows **Richardson Maturity Model level 2**: the action is carried by
the HTTP method on noun-based resource URIs (`GET/POST /products`,
`GET/PUT/DELETE /products/{id}`), collections are filtered with query parameters
(`GET /products?brand=&name=`), and responses use accurate status codes —
`201 Created` (+ `Location`) on create, `204 No Content` on delete, `200` on
read/update, and `404`/`409`/`401` on error. All paths are served under the
`api.prefix` (default `/api/v1`).

Collection endpoints are **paginated**: the product listing uses offset
pagination (`page`/`size`/`sort`, with a capped page size and an allowlisted
sort), while order history uses keyset (cursor) pagination for stable,
deep-scroll access.

### API documentation

Once the app is running, springdoc-openapi exposes:

- **Swagger UI** — `http://localhost:8080/swagger-ui.html`
- **OpenAPI spec** — `http://localhost:8080/v3/api-docs`

Both sit outside the `api.prefix` and are reachable without authentication. A
global `bearerAuth` (JWT) scheme is declared (`OpenApiConfig`), so click
**Authorize** in Swagger UI and paste the token from `POST /api/v1/auth/login`
to call the secured cart/order endpoints.

Both are **disabled in the `prod` profile** (`springdoc.api-docs.enabled=false`,
`springdoc.swagger-ui.enabled=false`) to remove needless attack surface.

### Wishlist, reminders & alerts

Customers save products to a wishlist and hear about them later through an
**in-app inbox** (no email). Every endpoint acts on the signed-in caller, so no
path carries a user id.

| Endpoint | Purpose |
|---|---|
| `GET /wishlist` | The caller's wishlist, newest first |
| `PUT /wishlist/items/{productId}` | Save a product — idempotent: `201` the first time, `200` after |
| `DELETE /wishlist/items/{productId}` | Remove it (`204`) |
| `PUT` / `DELETE /wishlist/items/{productId}/reminder` | Set (`{"remindAt": "<ISO instant, future>"}`) or clear a dated reminder |
| `PUT /wishlist/items/{productId}/alerts` | `{"enabled": bool}` — price-drop and back-in-stock alerts (on by default) |
| `GET /notifications` | The inbox, newest first, paginated |
| `GET /notifications/unread-count` | Unread count for the header badge |
| `POST /notifications/{id}/read`, `POST /notifications/read-all` | Mark read |
| `DELETE /notifications/{id}` | Dismiss |

- **Three kinds of notification.** A *reminder* fires once at the time the user
  picked and then clears. A *price drop* fires when the price goes below the
  lowest price already announced (initially the price when saved), so a price
  that bounces around doesn't spam. A *back-in-stock* alert fires when a
  sold-out item is restocked.
- **Scheduled scan.** `WishlistAlertJob` runs on `APP_WISHLIST_ALERT_CRON` (every
  minute by default) under **ShedLock**, so only one instance runs each tick. It
  *polls* price and stock rather than hooking every place they change (admin
  edits, orders, cancellation restocks), so no code path can forget to raise an
  alert. Each item gets a transaction of its own (a `TransactionTemplate`, as a
  loop can't be split into transactions with `@Transactional`), which writes the
  notification and advances the item's baseline together, so every event is
  announced exactly once. An item that fails is rolled back alone and retried on
  the next tick, without holding back anyone else's alerts.
- **Deletes never get blocked.** The new foreign keys cascade in the database
  (Flyway `V5`): deleting a user removes their wishlist and inbox; deleting a
  product removes it from wishlists and keeps past notifications (the product
  name is stored with them) with the link cleared.
- Someone else's notification id returns `404`, not `403`, so ids can't be
  probed for existence.

### Payments

Orders are paid for before they are fulfilled: checkout leaves an order `PENDING`
with its stock reserved, and a successful payment moves it to `PAID`. No real
money moves: by default a fake provider inside the app takes the payments, with
test payment methods (`test_visa`, `test_declined`, `test_insufficient_funds`,
`test_flaky`, `test_provider_down`, ...), and Stripe's test mode can take its place
([below](#stripe-in-test-mode)). The design, with its diagrams and trade-offs, is in
[docs/payment-design.md](docs/payment-design.md).

| Endpoint | Purpose |
|---|---|
| `POST /orders/{orderId}/payments` | Pay, with an `Idempotency-Key` header and `{"paymentMethod": "test_visa"}`: `201` for a new attempt (`SUCCEEDED`, or `FAILED` with the reason), `202` with `PROCESSING` when the provider didn't answer in time, `200` with the first answer when the key was already used |
| `GET /orders/{orderId}/payments` | The order's attempts, oldest first |
| `GET /orders/{orderId}/payments/{paymentId}` | One attempt |
| `POST /payments/webhooks` | Events from the payment provider (`charge.succeeded`, `charge.failed`, `refund.succeeded`, or Stripe's equivalents), with no user token: authenticated by their `Payment-Signature` (or Stripe's `Stripe-Signature`) |

- **Never charged twice.** A repeated request with the same key gets the first
  answer; the order row is locked while an attempt starts, so two attempts take
  turns; and the database allows one payment per order that hasn't failed.
- **No provider call inside a transaction.** The attempt is recorded, the provider
  called, and the answer recorded: two short transactions with the call between
  them, retried (with the same provider key) only while the provider is
  unavailable.
- **Cancelling a paid order refunds it.** The cancellation's transaction marks the
  payment `REFUND_PENDING` (an outbox row: owed if and only if the cancellation
  commits); after the commit, a background thread asks the provider and records
  `REFUNDED`. `RefundRetryJob` (`APP_PAYMENT_REFUND_RETRY_CRON`, every minute,
  ShedLock) retries any refund still owed, with the same provider key, so it is
  refunded once.
- **Lost outcomes are reconciled.** A payment still `PROCESSING` after 2 minutes
  (the provider timed out, or the app stopped before recording its answer) is
  settled by `PaymentReconciliationJob` (`APP_PAYMENT_RECONCILE_CRON`, every
  minute, ShedLock), which asks the provider what it did with the charge's key:
  approved, declined, or never received.
- **Unpaid orders expire.** Checkout reserves the stock, so an order still unpaid
  after 30 minutes (`APP_ORDERS_UNPAID_EXPIRY`) is cancelled and its items go back
  on sale, by `UnpaidOrderExpiryJob` (`APP_ORDERS_UNPAID_EXPIRY_CRON`, every
  minute, ShedLock), one transaction per order. An order being paid for is left
  until its payment settles. The order's `payBy` tells the customer the deadline.
- **Webhooks settle what the shop couldn't, in seconds.** The provider reports each
  charge and refund by a signed webhook: an HMAC-SHA256 over the timestamp and the
  raw body with `PAYMENT_WEBHOOK_SECRET`, checked in constant time, refused if
  more than 5 minutes old. Each event's id is recorded in the same transaction as
  its change, so a repeated delivery changes nothing. The simulated provider sends
  them to the API itself once both `PAYMENT_WEBHOOK_SECRET` and
  `APP_PAYMENT_FAKE_WEBHOOK_URL` are set (staging and production do); the jobs
  above still cover a webhook that never arrives.
- An order can't be cancelled while its payment is being processed, nor once it
  has shipped.

#### Stripe in test mode

The fake provider is the default, and what the tests, CI and staging use. To take
payments through Stripe's real API in test mode instead (free: no real money moves,
and the app refuses to start with a live key):

1. Create a Stripe account, and copy the test-mode secret key (`sk_test_...`) from
   **Developers → API keys** in the Stripe dashboard.
2. Add to `.env`, which git ignores (the key goes nowhere else):
   ```bash
   APP_PAYMENT_PROVIDER=stripe
   STRIPE_SECRET_KEY=sk_test_...
   ```
3. For Stripe's webhooks, have the [Stripe CLI](https://docs.stripe.com/stripe-cli)
   forward them to the API. Set `PAYMENT_WEBHOOK_SECRET` in `.env` to the `whsec_...`
   secret that `--print-secret` shows, then keep `stripe listen` running while you
   test. It forwards the four events the shop handles; the CLI (1.54 and later)
   insists they be named.
   ```bash
   brew install stripe/stripe-cli/stripe && stripe login
   stripe listen --print-secret
   stripe listen --events payment_intent.succeeded,payment_intent.payment_failed,refund.created,refund.updated \
     --forward-to localhost:8082/api/v1/payments/webhooks
   ```
4. Restart the API (`docker compose up -d app`) and pay with `test_visa`,
   `test_mastercard`, `test_declined` or `test_insufficient_funds`: the payments
   and refunds show up in the dashboard's test mode. The methods that simulate
   timeouts and outages work only with the fake provider.

How the adapter keeps Stripe's idempotency, failures and webhooks safe is in
[docs/payment-design.md, section 8](docs/payment-design.md#8-stripe-in-test-mode).

**A circuit breaker guards the calls to Stripe** (Resilience4j;
[section 9](docs/payment-design.md#9-circuit-breaker-on-stripe)). When half or more
of the last 10 calls failed or took over 10 seconds, it opens for 30 seconds:
Stripe isn't called, a payment gets `503` with `Retry-After: 30` instead of using
up one of the order's attempts, and refunds and reconciliation wait for their next
run. Then 2 trial calls go through, and it closes when they succeed. A decline
doesn't count, since that is Stripe answering. Its state is on the Grafana
dashboard and in the health details, as `CIRCUIT_OPEN`, which leaves health `UP`,
and it is never part of readiness, so an outage at Stripe never takes the shop
offline.

**Retries and fallbacks** (Resilience4j's Retry;
[section 10](docs/payment-design.md#10-retries-and-fallbacks-with-resilience4j)): a
provider call that can't reach the provider is tried 3 times in all, 100 ms and
200 ms apart. Then its fallback says what happened instead of throwing: a payment is
recorded as failed ("try again in a moment"), a refund stays owed for the retry job,
a lookup is asked again on the next run. A timeout isn't retried, since the provider
may have charged; reconciliation finds out. The fake provider's webhook delivery
and the startup check of external endpoints get retries too, and the delivery its
own breaker. Admins can see them all at `/actuator/circuitbreakers`,
`/actuator/circuitbreakerevents`, `/actuator/retries` and `/actuator/retryevents`.

## Startup pipeline

Initial setup is handled by ordered `ApplicationRunner` / `CommandLineRunner`
beans (catalog seeding/warm-up in `catalog`, diagnostics in `common.bootstrap`).
Spring sorts **all** runners together by
`@Order` and invokes them **ascending — lowest number runs first**. Gaps of 10
are left intentionally so new runners can be inserted (e.g. `@Order(25)`).

| Order | Runner                    | Type                | Responsibility                                        |
|:-----:|---------------------------|---------------------|-------------------------------------------------------|
| 10    | `StartupInfoRunner`       | `CommandLineRunner` | Log effective config/env (secrets masked)             |
| 20    | `DefaultDataRunner`       | `ApplicationRunner` | Seed default catalog **categories** (idempotent)      |
| 25    | `SampleProductRunner`     | `ApplicationRunner` | Seed demo **products**, each with a placeholder image (idempotent) |
| 30    | `ConnectivityCheckRunner` | `ApplicationRunner` | Validate DB + configured external API endpoints       |
| 40    | `CacheWarmupRunner`       | `ApplicationRunner` | Warm the `categories` cache                           |

All runners execute **before** `ApplicationReadyEvent`, after which
`DataInitializer` seeds the default roles (in every profile) and, under the `dev`
profile only, `DevDataSeeder` seeds the test accounts (5 customers, 2 admins).

### Startup configuration

```properties
# DefaultDataRunner — seed categories (disable per-run with the arg: --skip-seed)
app.startup.seed.enabled=true
app.startup.seed.categories=Electronics,Books,Clothing,Home & Kitchen,Toys,Sports,Beauty,Groceries

# SampleProductRunner — seed demo products (with a generated placeholder image),
# gated by seed.enabled above; the download URL is served by the images endpoint.
# The prod profile turns this off, so a real catalogue never gets the demo products.
app.startup.seed.products-enabled=true

# ConnectivityCheckRunner — DB is always checked; external endpoints are optional (comma-separated)
app.startup.connectivity.endpoints=
app.startup.connectivity.timeout-ms=3000

# CacheWarmupRunner — preload the category cache
app.startup.cache.warmup-enabled=true
```

A **database** connectivity failure is logged as an ERROR; external endpoint
failures are logged as WARN and do **not** abort startup.

These keys are bound to a validated, type-safe `@ConfigurationProperties` group
(`StartupProperties`, prefix `app.startup`) rather than scattered `@Value`
lookups — invalid config (e.g. a non-positive timeout, empty category list) fails
fast at startup. JWT settings are grouped the same way (`AuthTokenProperties`,
prefix `auth.token`). Defaults live in code, so the **same jar runs in every
environment** and only the externalized configuration changes.

### Caching
`CacheConfig` enables Spring's cache abstraction with an in-memory
`ConcurrentMapCacheManager`. `GET /categories` is served from the `categories`
cache (`CategoryService.getAllCategoryDtos`, which caches DTOs, not entities), and
every category create, rename or delete — including a product write that creates
a category — clears it with `@CacheEvict`. The manager is wrapped in a
`TransactionAwareCacheManagerProxy`, so a clear takes effect when its transaction
commits, never before. The cache is per instance: with several instances, swap
the manager for a shared one (Redis); the annotations stay unchanged.

### Cross-cutting logging (AOP)
`LoggingAspect` (package `common.aop`) is a single `@Around` aspect over every
public method of every `@Service`. Because each module holds its own services, a
layer is no longer a package, so layers are matched **by annotation** — the
shared pointcuts live in `Layers` (`services()`, `requestHandlers()`), limited to
this application's packages (otherwise library controllers such as springdoc's
would be advised too) and leaving out the per-request security infrastructure. It logs
method entry, successful exit with elapsed time at **DEBUG**, and failures with
the exception type/message at **ERROR** before rethrowing — so tracing and timing
live in one place instead of being scattered through each service, and the
handling path (`GlobalExceptionHandler`) is unchanged.

Auto-proxying is switched on explicitly by `AopConfig` (`@EnableAspectJAutoProxy`)
and the AspectJ annotations come from `aspectjweaver` — Boot 4 no longer ships
`spring-boot-starter-aop`, and `spring-aop` is already on the classpath via
spring-context. Being proxy-based, advice fires only when a service is called
**through its proxy** from another bean, not on self-invocation. Output is quiet
by default; enable it in dev with:

```yaml
logging:
  level:
    com.aryan.spring_security_demo.common.aop: DEBUG
```

## Default seed data

Seeded by `DevDataSeeder`, which exists **only under the `dev` profile** — these
accounts are never created in production.

| Type      | Credentials                                  |
|-----------|----------------------------------------------|
| Customers | `user1@gmail.com` … `user5@gmail.com` / `123456` |
| Admins    | `admin1@gmail.com`, `admin2@gmail.com` / `123456` |

> Development conveniences only. Roles (`ROLE_ADMIN`, `ROLE_CUSTOMER`) are seeded
> in every environment by `DataInitializer`; the test accounts above are not.
> Outside dev, set `ADMIN_EMAIL` and `ADMIN_PASSWORD` for the first start, and
> `AdminBootstrap` creates that account as the shop's admin.

## Build & test

```bash
./mvnw clean compile                 # compile every module
./mvnw test                          # every module's tests
./mvnw test -pl shop-cart -am        # one module, with the modules it uses
./mvnw clean package                 # build; the runnable jar is shop-app/target/shop-app-*.jar
./mvnw test -DexcludedGroups=integration            # unit tests only
./mvnw test -pl shop-app -am -Dgroups=integration   # integration tests only
./mvnw verify                        # all tests + coverage: shop-app/target/site/jacoco-aggregate/index.html
```

Unit tests live in the module whose code they test; the tests that need the
application context (`@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest`) are in
`shop-app`. The `@SpringBootTest` and `@DataJpaTest` ones are tagged `integration` and
run against MySQL in a container, so they need Docker.

**The API contract.** [`docs/openapi.json`](docs/openapi.json) is the OpenAPI spec the
app generates, committed: a change to an endpoint or a DTO shows up in its pull request
as a diff there, and `OpenApiContractTest` fails until it is regenerated:

```bash
./mvnw test -pl shop-app -am -Dtest=OpenApiContractTest -Dsurefire.failIfNoSpecifiedTests=false -Dopenapi.update=true
```

**Fuzzing.** The staging stage runs Schemathesis against the deployed API with that
contract: thousands of generated requests, valid and malformed, signed in as a customer,
and any 5xx fails the build. Its first run found five kinds of malformed request that
crashed with a 500 (a multipart body with no boundary, an undecodable query string, a
parameter name the firewall rejects, a `page` so large its offset overflowed, a `%` in
`sort`); they are 400s now. To fuzz a local API (dev profile on :8082):

```bash
docker run --rm -v "$PWD/docs:/spec:ro" schemathesis/schemathesis:4.31.1 run /spec/openapi.json \
  --url http://host.docker.internal:8082 --header "Authorization: Bearer <a customer's token>" \
  --checks not_a_server_error --exclude-operation-id deleteUser
```

## Load testing

[`load/checkout.js`](load/checkout.js) is a [k6](https://k6.io/) load test of the
purchase path. Up to 50 virtual shoppers each sign up, then loop: browse products, add
one to the cart, check out, pay (with an `Idempotency-Key`), view the paid order, and
wait a second. A second scenario reads the database connection pool (Hikari's active and
pending connections, from the actuator as an admin) once a second, so the summary shows
the pool next to the latencies. It passes when under 1% of requests fail, 99% of checks
pass, and p95 stays under 300 ms to browse, 800 ms to check out and 1.5 s to pay.

Run it against an API started with the `prod` profile (no SQL logging), with an admin
account and the auth rate limit raised for the test:

```bash
# the API, from the repository root (MySQL from docker-compose.yml)
APP_RATELIMIT_CAPACITY=100000 ADMIN_EMAIL=loadadmin@load.test ADMIN_PASSWORD=<12+ characters> \
  SPRING_PROFILES_ACTIVE=prod ./mvnw -pl shop-app -am spring-boot:run -Dspring-boot.run.arguments=--server.port=8082
# k6 (APP_PAYMENT_FAKE_LATENCY=300ms on the API simulates a slow payment provider)
ADMIN_EMAIL=loadadmin@load.test ADMIN_PASSWORD=<the same> docker run --rm -e ADMIN_EMAIL -e ADMIN_PASSWORD \
  -v "$PWD/load:/load:ro" grafana/k6:2.3.0 run /load/checkout.js -e BASE_URL=http://host.docker.internal:8082
```

Results on a laptop (Apple M1, 8 cores; MySQL in Docker on the same machine; one run each
of 2m20s, with the pool settings from before the tuning below; numbers from another machine
will differ):

| | Provider answers at once | Provider takes 300 ms per call |
|---|---|---|
| Requests (failed) | 19,811 (0%) | 17,023 (0%) |
| Throughput | 139 requests/s, 27.5 purchases/s | 120 requests/s, 24.5 purchases/s |
| p95 browse / check out / pay | 80 ms / 138 ms / 166 ms | 40 ms / 66 ms / 380 ms |
| Pool: connections in use, average (max of 10) | 5.9 (10) | 3.5 (10) |
| Pool: requests waiting for one, average / p90 / max | 3.4 / 13 / 19 | 0.3 / 0 / 9 |

- **A slow provider doesn't drain the pool**, the point of keeping its call outside any
  transaction. With 300 ms per call, paying takes about that much longer, but the pool
  is *less* busy: each purchase spends those 300 ms holding no connection. Had the call
  run inside the payment's transaction, at 24.5 payments a second it would have held
  about 7 of the 10 connections by itself (24.5 × 0.3 s).
- **At 50 shoppers the pool is the busiest resource.** Its 10 connections are all in use
  at peaks and requests briefly queue for one, which is Hikari's back-pressure: latency
  stays low and nothing fails. Every request also looks its user up in the database
  (the JWT filter), so that lookup is the first thing to cache if the shop needed more.

### Tuning the connection pool

The pool's settings in `application.yml`, with the reason for each beside it: a
fixed-size pool of `DB_POOL_SIZE` (10 by default; size it at about the database server's
CPU cores × 2 + 1, with every instance's pool together under MySQL's `max_connections`);
a 5-second wait for a connection, after which the request gets a **503 with
`Retry-After`** instead of hanging for HikariCP's default 30 seconds; connections retired
after 30 minutes (under MySQL's `wait_timeout`) and kept alive every 2 minutes (under the
idle timeouts of firewalls in front of a managed database); and the MySQL driver caching
prepared statements. `DataSourcePoolTest` checks the pool really runs with them.

Measured with the load test above (provider instant, same machine, runs back to back;
the same configuration varied by about ±5% between runs, so only differences that held
across runs count):

| Configuration | Throughput | p95 pay | Requests waiting for a connection (avg) |
|---|---|---|---|
| Before: `minimum-idle: 5`, 30 s timeout, driver defaults (2 runs) | 120–136 req/s | 206–397 ms | 3.9–7.7 |
| New pool settings, driver defaults | 138 req/s | 171 ms | 3.1 |
| New, with statements prepared on the server | 145 req/s | 135 ms | 2.3 |
| **New, with statements cached on the client (the setting chosen, 3 runs)** | 139–145 req/s | 118–176 ms | 1.4–3.3 |
| The same with 15 connections | 136 req/s | 181 ms | 2.2 |
| The same with 20 connections | 145 req/s | 116 ms | 0.5 |

- The old settings were the slowest in every round; statement caching was the biggest
  single gain.
- Statements prepared on the server measured no faster than cached on the client, and
  each one counts against MySQL's global `max_prepared_stmt_count`, which instances ×
  pool size × 250 cached statements can reach; so they stay on the client.
- **More connections didn't add throughput.** With 20 the queue for a connection all but
  vanished, but throughput and latency matched 10: the database and the CPU are the
  limit, not the pool. So the default stays 10.
- Two runs had 1 or 2 payments (of about 3,500) fail; the API logged no error for them,
  and they didn't recur once the test logged each failure's status, so they were most
  likely network blips between the k6 container and the host.

## CI/CD pipeline

[`.github/workflows/pipeline.yml`](.github/workflows/pipeline.yml) takes every change
through these stages:

```
commit ─▶ Build ─┬─▶ Unit tests ─▶ Integration tests ─┬─▶ Quality gate ──pass──▶ Build artifact ─▶ Staging: deploy + E2E ─▶ Deploy production
                 ├─▶ Security scan ────────────────────┤        │                                                             (after approval)
                 └─▶ Static analysis ──────────────────┘        │
                                                               fail
                                                                ▼
                                                          Failed build
```

- **Build** compiles every module (tests included) and builds the storefront.
- **Unit tests**: JUnit/Mockito and the Spring slices, plus Vitest, with coverage.
- **Integration tests**: the `@SpringBootTest` and `@DataJpaTest` tests, the whole API
  on MySQL 8.4 (Testcontainers) with the real Flyway migrations.
- **Static analysis**: SpotBugs with FindSecBugs (bugs, and security flaws in the
  code), PMD and CPD (copy-pasted code); any finding fails it. The same runs on a
  local `./mvnw verify`.
- **Security scan**: OWASP Dependency-Check and `npm audit`, failing on a HIGH or
  CRITICAL CVE (they also run daily on `master`), and gitleaks over the git history
  for committed secrets.
- **Quality gate**: runs only if all of the above passed, then holds backend and
  frontend coverage to the floors in
  [`quality_gate.py`](.github/scripts/quality_gate.py).
- **Build artifact**: the API and storefront images, scanned with Trivy (a fixable
  HIGH or CRITICAL CVE in the OS packages or the libraries inside fails it). On
  `master` they are pushed to GitHub Container Registry for amd64 and arm64, tagged
  with the commit SHA.
- **Staging (deploy + E2E)**: the production setup from [`deploy/`](deploy/), started
  on the CI runner from those images with a MySQL container, then the Playwright
  tests in [`frontend/e2e/`](frontend/e2e/) against it over HTTPS, and
  [Schemathesis](https://schemathesis.readthedocs.io/) fuzzing the API against its
  contract (see Build & test). On `master` the images that pass become `latest`.
- **Deploy production**: on `master`, after a reviewer approves, over SSH with
  [`deploy/deploy.sh`](deploy/deploy.sh), which rolls back if the new build doesn't
  become ready. Setup: [`deploy/README.md`](deploy/README.md).

Pull requests run every stage up to staging; nothing is pushed or deployed from them.

## Frontend

A customer-facing storefront (**React + Vite + TypeScript**) in
[`frontend/`](frontend/) consumes this API under `/api/v1`: register/login (JWT
with automatic refresh), browse/filter the catalog, product detail, cart,
checkout, order history, a wishlist with reminders and price/stock alerts,
and account management (edit profile, change password, delete account). In development a Vite proxy forwards `/api` to this
server, so **no CORS setup is needed**; for production, point `VITE_API_BASE_URL`
at the API origin.

```bash
# with the API running on :8080
cd frontend
npm install
npm run dev      # http://localhost:5173
npm test         # Vitest suite (37 tests, jsdom)
```

Client-side auth, a typed API client (envelope unwrap, problem+json errors,
single-flight token refresh), and a Vitest test suite are covered in
[`frontend/README.md`](frontend/README.md).

## Deploying

[`deploy/`](deploy/) runs the shop on one server with Docker Compose: Caddy in
front (automatic HTTPS, the storefront, `/api` proxied to the API) and a managed
MySQL. The CI/CD pipeline (above) builds both images, public and for both amd64 and
arm64, tests them on staging, and deploys them after approval. Steps, updates and
limits: [`deploy/README.md`](deploy/README.md).

## Docker

A multi-stage `Dockerfile` builds the jar with a JDK and ships it on a slim,
pinned JRE. The image is hardened: it runs as an **unprivileged user**, pins its
base images (never `:latest`), and a `.dockerignore` keeps build cruft and any
local config/secrets out of the build context.

**No configuration or secrets live in the image.** The jar carries only
non-secret defaults; every environment-specific value — and every secret — is
injected at **runtime**, so the same image runs everywhere.

```bash
docker build -t shopping-cart:latest .

docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL='jdbc:mysql://db:3306/shopping_cart' \
  -e DB_USERNAME='shop' \
  -e DB_PASSWORD='...' \
  -e JWT_SECRET='...' \
  shopping-cart:latest
```

> Never bake `DB_PASSWORD`, `JWT_SECRET`, or any key into the image or the
> `Dockerfile` — anyone who pulls the image could extract it. Supply them at
> runtime via `-e`, an env file kept out of source control, or a secrets manager.
> Kubernetes liveness/readiness probes should target `/actuator/health/liveness`
> and `/actuator/health/readiness` (the only public actuator endpoints).
