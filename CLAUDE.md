# CLAUDE.md

Shopping-cart app: a Spring Boot 4 / Java 17 REST API (JWT auth, MySQL + Flyway)
and a React + Vite + TypeScript storefront in `frontend/`. This file is read by
Claude Code in local sessions and by the Claude PR review on GitHub, which
checks every pull request against the **Rules** below. The rules apply to code
a PR adds or changes; older code may predate some of them.

## Commands

```bash
./mvnw test                          # backend tests (H2, `test` profile — no MySQL needed)
./mvnw test -Dtest=WishlistIntegrationTest
cd frontend && npm test              # Vitest
cd frontend && npm run build         # type-check + production build
```

Running locally: start the API with `SPRING_PROFILES_ACTIVE=dev` (the default is
`prod`), and with `--server.port=8082`, because the Vite dev server proxies `/api`
to **port 8082** (see README for the database settings).

## Layout

Backend code is organised by feature module under `com.aryan.spring_security_demo`:
`common` (config, aspects, error handler, response wrappers), `identity` (users,
auth, security), `catalog`, `cart`, `order`, `notification`, `wishlist`. There is
no app-wide component scan: `SpringSecurityDemoApplication` `@Import`s one
`XxxModule` class per module, and each scans only its own package.

## Rules

### Database and migrations
- Never edit a Flyway migration that is already on `master`; every schema change goes in a new `src/main/resources/db/migration/V<next>__<description>.sql`.
- A change to a JPA entity's tables or columns must come with a matching migration in the same PR, because dev and prod run `ddl-auto: validate` and refuse to start on a mismatch (the H2 tests build the schema from the entities and will not catch it).
- A new foreign key to `users` or `product` must declare what happens on delete (`on delete cascade` or `on delete set null` in the migration, with the matching `@OnDelete` on the entity field), otherwise deleting a user or product fails.
- Associations are `FetchType.LAZY`; load what a query needs with `join fetch` or `@BatchSize`, never by switching to `EAGER`.

### Security and data access
- Endpoints are authenticated by default (`identity/security/config/ShopConfig`); a new `permitAll()` or a changed role rule must have a comment beside it explaining why it is safe.
- Admin-only endpoints are restricted in `ShopConfig` with `hasAuthority("ROLE_ADMIN")`, not with `@PreAuthorize` on the controller.
- A service method that loads a user-owned record (cart, order, account, wishlist item, notification) using an id from the request must either call `authUtils.requireSelfOrAdmin(ownerId)` after loading it, or query only the current user's rows via `authUtils.currentUserId()`.
- Never trust a user id sent by the client to decide whose data to act on; take it from `AuthUtils`.
- Passwords are hashed only through the `PasswordEncoder` bean; passwords, JWTs and refresh tokens are never logged, and refresh tokens are stored only as hashes.
- No secrets in committed files (`application*.yml`, `Dockerfile`, `docker-compose.yml`, `.env.example`); secrets come from environment variables.

### Persistence and transactions
- `open-in-view` is off: convert entities to DTOs inside the `@Transactional` service method, and never let a controller accept or return a JPA entity in a new endpoint.
- Code that needs the current time uses the injected `java.time.Clock` bean, not `Instant.now()`, `LocalDate.now()` or `System.currentTimeMillis()`.

### API and errors
- Success responses are wrapped in `ApiResponse` (`{message, data}`).
- Errors are thrown as exceptions and turned into RFC 7807 responses by `common/exception/GlobalExceptionHandler`; controllers never build error responses themselves.
- A new exception type must be mapped in `GlobalExceptionHandler` to its 4xx status, or it surfaces as a 500.
- Request bodies are dedicated request classes validated with `@Valid` and Bean Validation annotations.
- Use `201 Created` with a `Location` header for creates, `204 No Content` for deletes, `404` for missing records, `409` for conflicts and `400` for invalid input.
- Endpoints that list data which grows without bound (products, orders, notifications) must be paginated.

### Modules and wiring
- New backend classes go in the package of the feature module they belong to.
- A new top-level module package needs its own `XxxModule` class annotated `@ModuleConfiguration`, added to `@Import` in `SpringSecurityDemoApplication`; otherwise none of its beans are loaded.
- Do not add `@SpringBootApplication`, `@ComponentScan`, `@EnableJpaRepositories` or `@EntityScan` anywhere.
- Aspects select layers with the named pointcuts in `common/aop/Layers`, not with package-based `execution(...)` expressions.

### Tests
- Unit tests are the default: service and domain logic is tested with plain JUnit and Mockito (no Spring context), and request validation with a `@WebMvcTest` slice.
- Integration tests (`@SpringBootTest` + MockMvc against H2) are only for security-sensitive and business-critical paths: authentication and sessions (login, tokens, passwords), access rules and ownership checks, money and stock (checkout, prices, inventory), and behavior a mock can't show (transactions, database constraints and cascades, cross-module events, security and actuator configuration).
- A new or changed access rule or ownership check has an integration test of its failure case (401, 403 or 404).
- Every bug fix comes with a test that fails without the fix: a unit test, unless the bug is in one of the integration-test areas above.
- `@SpringBootTest` tests use `@ActiveProfiles("test")`.
- Tests never `Thread.sleep` or depend on the wall clock; time-dependent code is tested with an explicit `Instant` or a fixed `Clock`.

### Frontend (`frontend/`)
- HTTP calls go through `request()` in `src/api/client.ts`, via a per-feature module in `src/api/`; components never call `fetch` directly.
- Server state uses React Query with keys from `src/api/queryKeys.ts`, and every mutation invalidates or updates the keys it affects.
- `queryFn` and `mutationFn` wrap API calls in arrow functions (`(id: number) => wishlistApi.remove(id)`), never pass the API function itself — React Query passes an extra context argument.
- When a backend DTO changes, `src/api/types.ts` changes in the same PR to match.
- A new page or component with logic has a Vitest + Testing Library test.
