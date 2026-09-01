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
| Boilerplate    | Lombok                                             |
| Build          | Maven wrapper (`./mvnw`)                           |

## Getting started

### Prerequisites
- JDK 17+
- MySQL running and reachable

### Configure

Configuration follows a **commit-safe-defaults, override-per-environment** model
— no config file needs to be copied or created to run locally:

- `application.yml` — committed **base** with shared, safe defaults.
- `application-dev.yml` / `application-prod.yml` — carry only what differs per
  environment (`ddl-auto`, SQL logging, log levels).
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
| `JWT_EXPIRATION_MS` | optional           | Token lifetime, default `3600000` (1h)             |

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
security/     config (shopConfig), jwt (AuthTokenFilter, JwtUtils, JwtEntryPoint), user details
config/       CacheConfig + OpenApiConfig + typed @ConfigurationProperties (StartupProperties, AuthTokenProperties)
bootstrap/    Ordered startup runners (see below)
data/         DataInitializer (roles, all envs) + DevDataSeeder (@Profile("dev") test users)
```

### Configuration & profiles
- **Typed, validated config**: `app.startup.*` and `auth.token.*` bind to
  `@ConfigurationProperties` beans (`@Validated`) rather than scattered `@Value` —
  invalid config fails fast at startup.
- **Profiles decide beans, not `if` checks**: `DevDataSeeder` is `@Profile("dev")`,
  so test users/admins exist only in dev; production never creates them. Roles
  (needed everywhere) are seeded unconditionally by `DataInitializer`.
- **`ddl-auto`**: `update` in dev, `validate` in prod (migrations own prod DDL).

### Security model
- **Stateless JWT**: `AuthTokenFilter` runs before `UsernamePasswordAuthenticationFilter`.
- Secured paths: `/api/v1/carts/**`, `/api/v1/cartItems/**`, `/api/v1/orders/**`.
  Everything else is currently `permitAll()` (which also exposes the Swagger docs).
- `@EnableMethodSecurity(prePostEnabled = true)` enables `@PreAuthorize`.
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
