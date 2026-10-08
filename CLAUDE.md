# CLAUDE.md

Shopping-cart app: a Spring Boot 4 / Java 17 REST API (JWT auth, MySQL + Flyway)
and a React + Vite + TypeScript storefront in `frontend/`. This file is read by
Claude Code in local sessions and by the Claude PR review on GitHub, which
checks every pull request against the **Rules** below. The rules apply to code
a PR adds or changes; older code may predate some of them.

## Commands

```bash
./mvnw test                          # every module's tests; the integration tests need Docker (MySQL via Testcontainers)
./mvnw test -pl shop-cart -am        # one module, with the modules it depends on
./mvnw test -pl shop-app -am -Dtest=WishlistIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
./mvnw test -DexcludedGroups=integration              # unit tests only (the pipeline's "Unit tests")
./mvnw test -pl shop-app -am -Dgroups=integration     # integration tests only ("Integration tests")
./mvnw verify                        # all tests, coverage report (shop-app/target/site/jacoco-aggregate/) and static analysis
./mvnw -Dmaven.test.skip=true verify # static analysis only: SpotBugs + FindSecBugs, PMD, CPD (the pipeline's "Static analysis")
./mvnw -DskipTests package dependency-check:aggregate   # OWASP CVE scan; report in target/ (set NVD_API_KEY to speed up the download)
cd frontend && npm test              # Vitest
cd frontend && npm run test:coverage # Vitest with coverage (coverage/index.html)
cd frontend && npm run build         # type-check + production build
cd frontend && npm audit --omit=dev --audit-level=high   # CVE scan of the packages that ship
cd frontend && npm run e2e           # Playwright, against a running deployment (see playwright.config.ts)
```

Running locally: the Vite dev server proxies `/api` to **port 8082**, and the
default profile is `prod`, so start the API with
`SPRING_PROFILES_ACTIVE=dev ./mvnw -pl shop-app -am spring-boot:run -Dspring-boot.run.arguments=--server.port=8082`
(see README for the database settings). Without `-am` Maven looks for the other
modules in the local repository, where they are never installed.

## CI/CD

`.github/workflows/pipeline.yml` runs every change through Build → Unit tests →
Integration tests (with Security scan and Static analysis alongside) → Quality gate → Build artifact →
Staging (deploy + E2E) → Deploy production; pull requests stop after staging, and
production needs a reviewer's approval. Staging is the production setup from
`deploy/` started on the CI runner from the new images, with
`deploy/docker-compose.staging.yml` adding a MySQL container; the Playwright tests in
`frontend/e2e/` run against it. The quality gate's coverage floors are in
`.github/scripts/quality_gate.py`. "Quality gate" and "Staging (deploy + E2E)" are
required checks for merging into `master`; production deploys need a reviewer's
approval in the `production` environment.

## Layout

The backend is a multi-module Maven build, one Maven module per feature module.
`shop-<name>` holds the package `com.aryan.spring_security_demo.<name>`:
`shop-common` (config, aspects, error handler, response wrappers), `shop-identity`
(users, auth, security), `shop-catalog`, `shop-cart`, `shop-order`,
`shop-notification`, `shop-wishlist`. `shop-app` holds the main class,
`application*.yml`, the Flyway migrations and the tests that need the application
context; it depends on all the others and builds the runnable jar. The parent
`pom.xml` holds the module list and every version.

Dependencies point one way, and Maven enforces it (a module sees only the modules
it declares): every module may use `shop-common`; `shop-identity` and
`shop-catalog` ← `shop-cart` ← `shop-order`; `shop-identity` and `shop-catalog` ←
`shop-notification` ← `shop-wishlist`.

There is no app-wide component scan: `SpringSecurityDemoApplication` `@Import`s one
`XxxModule` class per module, and each scans only its own package.

## Rules

### Database and migrations
- Never edit a Flyway migration that is already on `master`; every schema change goes in a new `shop-app/src/main/resources/db/migration/V<next>__<description>.sql`.
- A change to a JPA entity's tables or columns must come with a matching migration in the same PR, because dev, prod and the integration tests run `ddl-auto: validate` against the Flyway schema and fail on a mismatch.
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
- A new exception type extends one of the base types in `common/exception`, which `GlobalExceptionHandler` maps: `ResourceNotFoundException` (404), `ConflictException` (409), `BadRequestException` (400), `FieldValidationException` (400 with a field error) or `AuthenticationFailedException` (401). One that extends none of them surfaces as a 500.
- Request bodies are dedicated request classes validated with `@Valid` and Bean Validation annotations.
- Use `201 Created` with a `Location` header for creates, `204 No Content` for deletes, `404` for missing records, `409` for conflicts and `400` for invalid input.
- Endpoints that list data which grows without bound (products, orders, notifications) must be paginated.

### Modules and wiring
- New backend classes go in the feature module (Maven module and package) they belong to.
- A module depends only on the modules shown in the Layout graph. Never add a dependency the other way, which Maven would reject as a cycle: move the shared piece into `shop-common`, or have the lower module publish an event the higher one listens to (as `UserDeletingEvent` and `ProductDeletingEvent` do).
- A new feature module is a new Maven module `shop-<name>`: listed in the parent pom's `<modules>` and `<dependencyManagement>`, and a dependency of `shop-app`. It needs its own `XxxModule` class annotated `@ModuleConfiguration`, added to `@Import` in `SpringSecurityDemoApplication`; otherwise none of its beans are loaded.
- Versions live only in the parent pom (a property applied through `<dependencyManagement>`, or a BOM); a module pom never declares a `<version>`.
- Do not add `@SpringBootApplication`, `@ComponentScan`, `@EnableJpaRepositories` or `@EntityScan` anywhere.
- Aspects select layers with the named pointcuts in `common/aop/Layers`, not with package-based `execution(...)` expressions.

### Dependencies and vulnerabilities (CVEs)
- The pipeline scans for vulnerabilities on every push and pull request (the dependency scans also daily on `master`): the Security scan stage runs OWASP Dependency-Check (configured in the parent pom) for the backend, `npm audit --omit=dev --audit-level=high` for the frontend and gitleaks over the git history for secrets; the Build artifact stage scans each image with Trivy (OS packages and the libraries inside). The vulnerability scans fail on a HIGH or CRITICAL finding (CVSS 7 or above); Trivy only on one that has a fix. Dependabot alerts also report new CVEs in the frontend's packages (GitHub's dependency graph doesn't see the backend's resolved versions), and Dependabot (`.github/dependabot.yml`) opens weekly update pull requests. A finding is fixed by moving to a fixed version, never by raising the threshold, skipping the scan or dismissing the alert; Critical and High CVEs in what ships (the backend's runtime dependencies, the frontend's `dependencies`, the base images) are fixed before the next deploy.
- Keep the BOMs current: Spring Boot's patch releases carry the security fixes for most of the stack (Spring, Tomcat, Jackson, Netty, Logback, MySQL Connector/J, ...), so `spring-boot-starter-parent` moves to each new patch release of its line, and the other BOMs and version properties in the parent pom (ShedLock, springdoc, JJWT, ModelMapper) are kept current the same way.
- A CVE in a library the Boot BOM manages: upgrade Boot to the release with the fix. If Boot has none yet, override Boot's version property in the parent pom's `<properties>` (the names are in `spring-boot-dependencies`: `tomcat.version`, `jackson-bom.version` for Jackson 3, `jackson-2-bom.version` for Jackson 2, ...).
- A CVE in a library Boot doesn't manage: raise its version property in the parent pom; in a transitive dependency: pin the fixed version in the parent's `<dependencyManagement>`. Never fix it in a module pom (no `<version>`, no `<exclusions>` that swap in another artifact).
- Every forced override is documented with its CVE: a Boot version property, a `<dependencyManagement>` pin, an npm `overrides` entry and a Dependency-Check suppression each name the CVE ids, the dependency that brings in the vulnerable version, and when to remove the override (the release that will include the fix). That is an XML comment beside it in the poms, a line in the `"//"` array beside `overrides` in `package.json`, and the suppression's `<notes>` in `dependency-check-suppressions.xml`. Suppressions are only for false positives (the CVE is for another product, or for code the app never loads); the same goes for `.trivyignore` (a comment with the reason above each CVE id) and `.gitleaksignore` (a leaked secret is rotated first, then listed with that noted).
- Show that the fix took effect: `./mvnw -pl shop-app -am dependency:tree -Dincludes=<groupId>:<artifactId>` lists only the fixed version, and `./mvnw dependency:list` before and after shows only the intended artifacts moved (a BOM can move others; see the JJWT note in `pom.xml`).
- Frontend: upgrade the package (`npm install <package>@<fixed version>`) and commit the updated `package-lock.json`. Use `overrides` only for a transitive package whose parent has no fixed release. Never run `npm audit fix --force`, which makes major upgrades unasked.
- Base images (`eclipse-temurin:17-jre-jammy`, `caddy:2-alpine`, and the build stages' `eclipse-temurin:17-jdk-jammy`, `node:22-alpine`) are pinned to digests (`tag@sha256:...`); OS package fixes arrive as Dependabot pull requests that move the digest, and deploy like any other change. Never drop a digest to float on the tag. When a tag line reaches end of life, move the `Dockerfile` to a supported one.
- A CVE fix PR names the CVE ids, the package and the old and fixed versions, and passes the full suites (`./mvnw test`, `npm test`, `npm run build`). An upgrade across a major version is a PR of its own, unless the fix exists only in the new major.
- The repository is public. A vulnerability in this project's own code (as opposed to a published CVE in a dependency) is never described in a public issue, pull request, commit message or code comment before the fix is deployed: it is handled in a private GitHub security advisory, and the fix's PR and commits describe the change, not how to exploit the old behavior.

### Tests
- Unit tests are the default: service and domain logic is tested with plain JUnit and Mockito (no Spring context), and request validation with a `@WebMvcTest` slice.
- Unit tests live in the module whose code they test. Tests that start a Spring context from the application (`@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest`) or read `application*.yml` live in `shop-app`, the only module with the main class and the config files.
- Integration tests (`@SpringBootTest` + MockMvc against MySQL in Testcontainers, with the real migrations) are only for security-sensitive and business-critical paths: authentication and sessions (login, tokens, passwords), access rules and ownership checks, money and stock (checkout, prices, inventory), and behavior a mock can't show (transactions, database constraints and cascades, cross-module events, security and actuator configuration).
- A new or changed access rule or ownership check has an integration test of its failure case (401, 403 or 404).
- Every bug fix comes with a test that fails without the fix: a unit test, unless the bug is in one of the integration-test areas above.
- `@SpringBootTest` tests use `@ActiveProfiles("test")` and `@Tag("integration")`, and so do `@DataJpaTest` tests (they use the test profile's MySQL); the tag puts them in the pipeline's Integration tests stage, and everything without it runs as a unit test, which must not need Docker. All of them share one MySQL for the run, so each test sets up and clears the data it relies on.
- Tests never `Thread.sleep` or depend on the wall clock; time-dependent code is tested with an explicit `Instant` or a fixed `Clock`.

### Pipeline and deployment
- Never lower a coverage floor in `.github/scripts/quality_gate.py`, skip a stage, or loosen a check to get a change through; fix the change or add the tests.
- The build fails on a compiler warning and on any SpotBugs, FindSecBugs, PMD or CPD finding. Fix the code; an exclusion (`spotbugs-exclude.xml`, a PMD `@SuppressWarnings("PMD.RuleName")`) is only for a finding that is wrong for that code, with a comment saying why.
- A new user-facing journey (a page or flow a customer or admin depends on) gets an E2E test in `frontend/e2e/` (`*.e2e.ts`), and a change that breaks one updates it in the same PR. E2E tests create their own data through the API and never depend on what is already in the database.
- The staging setup stays the production setup: `deploy/docker-compose.staging.yml` only adds what the CI runner lacks (the database container) and test-only settings, each with a comment saying why.
- The production setup changes only through `deploy/` (`docker-compose.yml`, `Caddyfile`, `deploy.sh`): every deployment copies those files to the server, so an edit made to them there is overwritten.

### Frontend (`frontend/`)
- HTTP calls go through `request()` in `src/api/client.ts`, via a per-feature module in `src/api/`; components never call `fetch` directly.
- Server state uses React Query with keys from `src/api/queryKeys.ts`, and every mutation invalidates or updates the keys it affects.
- `queryFn` and `mutationFn` wrap API calls in arrow functions (`(id: number) => wishlistApi.remove(id)`), never pass the API function itself — React Query passes an extra context argument.
- When a backend DTO changes, `src/api/types.ts` changes in the same PR to match.
- A new page or component with logic has a Vitest + Testing Library test.
