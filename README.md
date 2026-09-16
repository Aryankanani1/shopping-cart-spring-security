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
(batching, sequence pooling, optimistic locking, `open-in-view=false`), and
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
| Observability  | Spring Boot Actuator (health probes, metrics)      |
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

The active profile defaults to `dev`; select prod with `SPRING_PROFILES_ACTIVE=prod`.

Environment variables:

| Variable          | Required?            | Purpose                                            |
|-------------------|----------------------|----------------------------------------------------|
| `JWT_SECRET`      | **always**           | Base64 key, ≥256 bits — `openssl rand -base64 32`  |
| `DB_URL`          | prod (dev: localhost)| JDBC URL, e.g. `jdbc:mysql://localhost:3306/shop`  |
| `DB_USERNAME`     | prod (dev: `root`)   | Database user                                      |
| `DB_PASSWORD`     | prod (dev: empty)    | Database password                                  |
| `SPRING_PROFILES_ACTIVE` | optional      | Active profile, default `dev`                      |
| `JWT_EXPIRATION_MS` | optional           | Access-token lifetime, default `900000` (15m)      |
| `JWT_REFRESH_EXPIRATION_MS` | optional   | Refresh-token lifetime, default `604800000` (7d)   |
| `APP_RATELIMIT_EVICTION_CRON` | optional | Sweep of replenished rate-limit buckets, default hourly |

In **dev** the datasource falls back to a local MySQL (`localhost:3306`,
`root`, empty password) so the app boots out of the box; any value can still be
overridden by exporting the matching env var.

### Run

```bash
# dev (default profile)
JWT_SECRET=$(openssl rand -base64 32) ./mvnw spring-boot:run

# prod
SPRING_PROFILES_ACTIVE=prod DB_URL=... DB_USERNAME=... DB_PASSWORD=... \
  JWT_SECRET=... ./mvnw spring-boot:run
```

The API is served under the `api.prefix` (default `/api/v1`).

## Architecture

```
controller/   REST controllers (Auth, Product, Cart, CartItem, Category, Image, Order, User)
Service/      Interface + impl per domain (+ cache/ for read-through catalog caching)
repository/   Spring Data JPA repositories
model/        JPA entities (User, Role, Product, Category, Image, Cart, CartItem, Order, OrderItem)
dto/ request/ response/   API boundary objects
security/     config (shopConfig), jwt (AuthTokenFilter, JwtUtils, JwtEntryPoint), ratelimit (RateLimitFilter/Service), user details
aop/          LoggingAspect — @Around advice logging service-layer entry/exit/timing
config/       CacheConfig + AopConfig + OpenApiConfig + typed @ConfigurationProperties (StartupProperties, AuthTokenProperties)
bootstrap/    Ordered startup runners (see below)
data/         DataInitializer (roles, all envs) + DevDataSeeder (@Profile("dev") test users)
resources/    application*.yml + db/migration/ (Flyway migrations: V1__baseline.sql, …)
```

### Configuration & profiles
- **Typed, validated config**: `app.startup.*` and `auth.token.*` bind to
  `@ConfigurationProperties` beans (`@Validated`) rather than scattered `@Value` —
  invalid config fails fast at startup.
- **Profiles decide beans, not `if` checks**: `DevDataSeeder` is `@Profile("dev")`,
  so test users/admins exist only in dev; production never creates them. Roles
  (needed everywhere) are seeded unconditionally by `DataInitializer`.
- **Flyway owns the schema** in every environment: versioned SQL migrations under
  `resources/db/migration` build the DDL, and Hibernate runs `ddl-auto: validate`
  in **both dev and prod** — it only checks the entities match the schema, never
  mutates it. Tests use H2 with `create-drop` and disable Flyway.

### Database migrations

Flyway applies the migrations at startup, before Hibernate validates. The baseline
`V1__baseline.sql` was generated from the entities via Hibernate's `MySQLDialect`
export, so `ddl-auto: validate` accepts it verbatim. All later changes go in new
`V2__…`, `V3__…` files — never edit an applied migration. `baseline-on-migrate`
is enabled so Flyway can adopt a pre-existing database (e.g. a dev schema built by
an earlier `ddl-auto: update`) instead of failing on a non-empty schema.

> **Boot 4 note**: the migrations only run because `spring-boot-starter-flyway` is
> on the classpath. Boot 4 moved the autoconfiguration into a per-module jar, so
> raw `flyway-core` alone leaves Flyway inert and prod dies on `validate`.

### Observability

Spring Boot Actuator exposes only `health`, `info`, and `metrics` over HTTP.
`/actuator/health` (and the `liveness` / `readiness` probe groups) is the only
public actuator endpoint — for load balancers and Kubernetes probes — while the
rest require authentication. In prod, health `show-details` is `never`, so the
probe returns just `UP`/`DOWN` and never leaks internals.

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
- **Rate-limited auth endpoints**: `RateLimitFilter` sits ahead of authentication in
  the chain and throttles `/auth/**` (login, refresh, logout) per client IP with an
  in-memory token bucket (`RateLimitService`) — `capacity` requests may burst, then
  the bucket refills to full over `refill-period`. Over-limit callers get `429 Too
  Many Requests` (RFC 7807 body + `Retry-After`) *before* any credential/token work,
  which blunts brute-force and credential stuffing. Tight defaults (5 req / 1 min)
  because only the auth endpoints are guarded. Single-instance by design (like the
  in-memory cache); behind a load balancer, back it with a shared store — the call
  site doesn't change.
- Roles: `ROLE_ADMIN`, `ROLE_CUSTOMER`.

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

## Startup pipeline

Initial setup is handled by ordered `ApplicationRunner` / `CommandLineRunner`
beans in the `bootstrap` package. Spring sorts **all** runners together by
`@Order` and invokes them **ascending — lowest number runs first**. Gaps of 10
are left intentionally so new runners can be inserted (e.g. `@Order(25)`).

| Order | Runner                    | Type                | Responsibility                                        |
|:-----:|---------------------------|---------------------|-------------------------------------------------------|
| 10    | `StartupInfoRunner`       | `CommandLineRunner` | Log effective config/env (secrets masked)             |
| 20    | `DefaultDataRunner`       | `ApplicationRunner` | Seed default catalog **categories** (idempotent)      |
| 30    | `ConnectivityCheckRunner` | `ApplicationRunner` | Validate DB + configured external API endpoints       |
| 40    | `CacheWarmupRunner`       | `ApplicationRunner` | Warm the `categories` / `products` caches             |

All runners execute **before** `ApplicationReadyEvent`, after which
`DataInitializer` seeds the default roles and users (5 customers, 2 admins).

### Startup configuration

```properties
# DefaultDataRunner — seed categories (disable per-run with the arg: --skip-seed)
app.startup.seed.enabled=true
app.startup.seed.categories=Electronics,Books,Clothing,Home & Kitchen,Toys,Sports,Beauty,Groceries

# ConnectivityCheckRunner — DB is always checked; external endpoints are optional (comma-separated)
app.startup.connectivity.endpoints=
app.startup.connectivity.timeout-ms=3000

# CacheWarmupRunner — preload read-heavy catalog caches
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
`ConcurrentMapCacheManager` (no extra dependency). `CatalogCacheService` exposes
`@Cacheable` reads where fetch **and** DTO conversion happen inside one read-only
transaction — required because `spring.jpa.open-in-view=false`. Swap the cache
manager for Redis/Caffeine in production; the annotations stay unchanged.

### Cross-cutting logging (AOP)
`LoggingAspect` (package `aop`) is a single `@Around` aspect over every public
method of the service layer (`execution(public * ...service..*.*(..))`). It logs
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
    com.aryan.spring_security_demo.aop: DEBUG
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

## Build & test

```bash
./mvnw clean compile   # compile
./mvnw test            # run tests
./mvnw clean package   # build the jar
```

## Frontend

A customer-facing storefront (**React + Vite + TypeScript**) in
[`frontend/`](frontend/) consumes this API under `/api/v1`: register/login (JWT
with automatic refresh), browse/filter the catalog, product detail, cart,
checkout, and order history. In development a Vite proxy forwards `/api` to this
server, so **no CORS setup is needed**; for production, point `VITE_API_BASE_URL`
at the API origin.

```bash
# with the API running on :8080
cd frontend
npm install
npm run dev      # http://localhost:5173
npm test         # Vitest suite (27 tests, jsdom)
```

Client-side auth, a typed API client (envelope unwrap, problem+json errors,
single-flight token refresh), and a Vitest test suite are covered in
[`frontend/README.md`](frontend/README.md).

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
