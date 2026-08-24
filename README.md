# Shopping Cart — Spring Security

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen)
![Spring Security](https://img.shields.io/badge/Spring%20Security-JWT-green)
![Database](https://img.shields.io/badge/MySQL-Hibernate-blue)
![Build](https://img.shields.io/badge/build-Maven-red)
![Last commit](https://img.shields.io/github/last-commit/Aryankanani1/shopping-cart-spring-security)

An e-commerce REST API built with **Spring Boot 4.1** and **Java 17**, featuring
JWT-based stateless authentication, role-based authorization, a product catalog,
cart, and order flow. Persistence is tuned for Hibernate best practices
(batching, sequence pooling, optimistic locking, `open-in-view=false`).

## Tech stack

| Concern        | Choice                                             |
|----------------|----------------------------------------------------|
| Framework      | Spring Boot 4.1.0 (Web MVC, Data JPA, Security)    |
| Language       | Java 17                                            |
| Database       | MySQL (`mysql-connector-j`), Hibernate             |
| Auth           | JWT (jjwt 0.12.5), stateless, BCrypt               |
| Mapping        | ModelMapper 3.2.4                                  |
| Caching        | Spring Cache (`ConcurrentMapCacheManager`)         |
| Boilerplate    | Lombok                                             |
| Build          | Maven wrapper (`./mvnw`)                           |

## Getting started

### Prerequisites
- JDK 17+
- MySQL running and reachable

### Configure
Copy the example properties and fill in the placeholders (secrets come from
environment variables, never source control):

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Required environment variables:

| Variable          | Purpose                                              |
|-------------------|------------------------------------------------------|
| `DB_URL`          | JDBC URL, e.g. `jdbc:mysql://localhost:3306/shop`    |
| `DB_USERNAME`     | Database user                                        |
| `DB_PASSWORD`     | Database password                                    |
| `JWT_SECRET`      | Base64 key, ≥256 bits — `openssl rand -base64 32`    |
| `JWT_EXPIRATION_MS` | (optional) token lifetime, default `3600000` (1h)  |

### Run

```bash
./mvnw spring-boot:run
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
config/       CacheConfig (@EnableCaching + cache manager)
bootstrap/    Ordered startup runners (see below)
data/         DataInitializer — seeds roles + users on ApplicationReadyEvent
```

### Security model
- **Stateless JWT**: `AuthTokenFilter` runs before `UsernamePasswordAuthenticationFilter`.
- Secured paths: `/api/v1/carts/**`, `/api/v1/cartItems/**`, `/api/v1/orders/**`.
  Everything else is currently `permitAll()`.
- `@EnableMethodSecurity(prePostEnabled = true)` enables `@PreAuthorize`.
- Roles: `ROLE_ADMIN`, `ROLE_CUSTOMER`.

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

### Caching
`CacheConfig` enables Spring's cache abstraction with an in-memory
`ConcurrentMapCacheManager` (no extra dependency). `CatalogCacheService` exposes
`@Cacheable` reads where fetch **and** DTO conversion happen inside one read-only
transaction — required because `spring.jpa.open-in-view=false`. Swap the cache
manager for Redis/Caffeine in production; the annotations stay unchanged.

## Default seed data

| Type      | Credentials                                  |
|-----------|----------------------------------------------|
| Customers | `user1@gmail.com` … `user5@gmail.com` / `123456` |
| Admins    | `admin1@gmail.com`, `admin2@gmail.com` / `123456` |

> These are development conveniences — disable seeding or change credentials
> before any non-local deployment.

## Build & test

```bash
./mvnw clean compile   # compile
./mvnw test            # run tests
./mvnw clean package   # build the jar
```
