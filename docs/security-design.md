# Security design

> Condensed from **version 1.0** (October 2026); the full text is at the tag
> [`security-design-v1.0`](https://github.com/Aryankanani1/shopping-cart-spring-security/blob/security-design-v1.0/docs/security-design.md).
> A learning resource, not a security audit. To report a security problem, see
> [SECURITY.md](../SECURITY.md); please don't open a public issue for one.

How the API decides who is calling and what they may do, drawn from the classes
that do it. Access tokens are JWTs signed RS256 with a private key only the API
holds, checked with the published public key (section 14).

## 1. The road a request travels

Solid arrows: a request that gets through. Dotted: where it can stop, with the
status and the class that writes it.

```mermaid
flowchart TB
    B["Browser<br/>Authorization: Bearer token"] --> C["Caddy<br/>TLS, HSTS, nosniff"]
    C --> R
    subgraph SEC["Spring Security filter chain, ShopConfig"]
        R["RateLimitFilter<br/>only /api/v1/auth/**"] --> T["BearerTokenAuthenticationFilter<br/>who is calling?"]
        T --> Z["AuthorizationFilter<br/>may they call this URL?"]
    end
    subgraph APP["Spring MVC and the services"]
        CT["@RestController<br/>@Valid request body"] --> S["Service and AuthUtils<br/>is this record theirs?"]
    end
    Z --> CT
    S --> OK["2xx<br/>ApiResponse: message, data"]
    C -. "plain HTTP" .-> X0["redirected to HTTPS"]
    R -. "too many tries" .-> X1["429 and Retry-After<br/>RateLimitFilter"]
    T -. "token invalid" .-> X7["401<br/>JwtEntryPoint"]
    Z -. "not signed in" .-> X2["401<br/>JwtEntryPoint"]
    Z -. "signed in, not an admin" .-> X3["403<br/>ApiAccessDeniedHandler"]
    CT -. "invalid body" .-> X4["400<br/>GlobalExceptionHandler"]
    S -. "no such record" .-> X5["404<br/>GlobalExceptionHandler"]
    S -. "not theirs" .-> X6["403<br/>AuthUtils, GlobalExceptionHandler"]
    classDef no stroke:#d1242f,stroke-width:2px
    classDef yes stroke:#1a7f37,stroke-width:2px
    class X1,X2,X3,X4,X5,X6,X7 no
    class OK yes
```

| Spring Security piece | Here |
|---|---|
| `SecurityFilterChain` | `ShopConfig.securityFilterChain`, and a second chain for Prometheus (section 10) |
| `BearerTokenAuthenticationFilter` | Spring's own, from `oauth2ResourceServer()` |
| `JwtDecoder`, `JwtEncoder` | `NimbusJwtDecoder`, `NimbusJwtEncoder` in `JwtConfig`, keys from `JwtKeys` |
| `Converter<Jwt, AbstractAuthenticationToken>` | `ShopJwtAuthenticationConverter`, which loads the user |
| `OncePerRequestFilter` | `RateLimitFilter` |
| `UserDetailsService`, `UserDetails` | `ShopUserDetailsService`, `ShopUserDetails` (with the user's id) |
| `AuthenticationManager`, `DaoAuthenticationProvider` | beans in `ShopConfig`, called by `AuthController.login` |
| `PasswordEncoder` | `BCryptPasswordEncoder` |
| `SecurityContextHolder` | filled by the bearer filter, read by `AuthUtils` |
| `AuthenticationEntryPoint`, `AccessDeniedHandler` | `JwtEntryPoint` (401), `ApiAccessDeniedHandler` (403) |
| `CorsConfigurationSource` | `CorsConfig` |

Errors inside the filter chain never reach `GlobalExceptionHandler`, so the
filters write them themselves, as the same RFC 7807 JSON.

Code: [`ShopConfig`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/config/ShopConfig.java),
[`GlobalExceptionHandler`](../shop-common/src/main/java/com/aryan/spring_security_demo/common/exception/GlobalExceptionHandler.java).

## 2. 401 or 403?

```mermaid
flowchart LR
    A["AuthorizationFilter<br/>a rule says no"] -->|AccessDeniedException| E{"ExceptionTranslationFilter<br/>is the caller anonymous?"}
    E -->|"yes: no valid JWT"| U["401 Unauthorized<br/>JwtEntryPoint.commence"]
    E -->|"no: signed in, wrong role"| F["403 Forbidden<br/>ApiAccessDeniedHandler.handle"]
```

Code: [`JwtEntryPoint`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/jwt/JwtEntryPoint.java),
[`ApiAccessDeniedHandler`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/ApiAccessDeniedHandler.java).

## 3. Signing in

```mermaid
sequenceDiagram
    autonumber
    participant B as Browser
    participant C as AuthController
    participant P as DaoAuthenticationProvider
    participant U as ShopUserDetailsService
    participant E as BCryptPasswordEncoder
    participant J as JwtUtils
    participant R as RefreshTokenService
    B->>C: POST /api/v1/auth/login (email, password)
    note over B,C: RateLimitFilter lets it through first
    C->>P: authenticationManager.authenticate(email, password)
    P->>U: loadUserByUsername(email)
    U-->>P: ShopUserDetails: id, email, BCrypt hash, roles
    P->>E: matches(password, hash)
    E-->>P: true
    P-->>C: Authentication, principal = ShopUserDetails
    C->>J: generateUserTokenFromUser(authentication)
    note over J: JwtEncoder signs it RS256 with the private key
    J-->>C: access token: a JWT, kid = key id, subject = email, claims id and roles, 15 minutes
    C->>R: issueFor(userId)
    note over R: 256 random bits, stored only as their SHA-256, valid 7 days
    R-->>C: refresh token (the raw value)
    C-->>B: 200 with id, token, refreshToken
```

An unknown email and a wrong password get the same `401`, so the answer can't
reveal which emails have accounts.

Code: [`AuthController.login`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/AuthController.java),
[`ShopUserDetailsService`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/user/ShopUserDetailsService.java),
[`JwtUtils`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/jwt/JwtUtils.java),
[`RefreshTokenService.issueFor`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/RefreshTokenService.java).

## 4. Every request after that

No server-side session (`STATELESS`): the caller is rebuilt from the token on every
request.

```mermaid
sequenceDiagram
    autonumber
    participant B as Browser (Ana)
    participant F as BearerTokenAuthenticationFilter
    participant J as JwtDecoder (JwtConfig)
    participant U as ShopJwtAuthenticationConverter
    participant H as SecurityContextHolder
    participant Z as AuthorizationFilter
    participant S as OrderController and OrderService
    B->>F: GET /api/v1/orders/42 with Authorization: Bearer eyJ...
    F->>J: decode(token)
    note over J: RS256 only, under a published key (the kid in the header),<br/>not expired by the injected Clock, issuer is this API
    J-->>F: Jwt: subject = ana@example.com, id = 7
    F->>U: convert(jwt)
    note over U: ShopUserDetailsService.loadUserByUsername(email),<br/>and the token's id must be the account's
    U-->>F: ShopUserDetails: id 7, ROLE_CUSTOMER, read from the database
    F->>H: setAuthentication(principal, authorities)
    F->>Z: filterChain.doFilter(request, response)
    Z->>H: getAuthentication()
    H-->>Z: Ana, ROLE_CUSTOMER
    note over Z: rule anyRequest().authenticated(): allowed
    Z->>S: getOrderById(42)
    S->>H: authUtils.requireSelfOrAdmin(owner 7)
    note over S,H: the same context, no database trip
    S-->>B: 200 with order 42
```

- The user is reloaded from the database every time, so a deleted account or a
  removed role takes effect at once, and the token must name the account's id.
- An invalid token is a `401` on any URL, public ones too (RFC 6750).

Code: [`JwtConfig.jwtDecoder`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/jwt/JwtConfig.java),
[`ShopJwtAuthenticationConverter`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/jwt/ShopJwtAuthenticationConverter.java),
[`AuthUtils`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/AuthUtils.java).

## 5. The access map

All URL rules are in `ShopConfig.securityFilterChain`, checked top to bottom.

| # | Method | Path | Rule | Why |
|---|---|---|---|---|
| 1 | `PUT` | `/api/v1/auth/password` | authenticated | Needs a signed-in caller; rate-limited like login |
| 2 | any | `/api/v1/auth/**` | permitAll | Login, refresh, logout |
| 3 | `POST` | `/api/v1/users` | permitAll | Signing up |
| 4 | `GET` | `/api/v1/.well-known/jwks.json` | permitAll | Public keys; nothing can be signed with them |
| 5 | `POST` | `/api/v1/payments/webhooks` | permitAll | Authenticated by its HMAC signature (section 10) |
| 6 | `GET` | `/products/**`, `/categories/**`, `/images/**` | permitAll | Browsing the catalogue |
| 7 | any | `/swagger-ui/**`, `/v3/api-docs/**` | permitAll | API docs; off in `prod` |
| 8 | any | actuator `health` | permitAll | Probes; details for admins only |
| 9 | any | every other actuator endpoint | `ROLE_ADMIN` | For operators |
| 10 | any | `/error` | permitAll | So an error can be shown |
| 11 | `POST`, `PUT`, `DELETE` | `/products/**`, `/categories/**`, `/images/**` | `ROLE_ADMIN` | Changing the catalogue |
| 12 | `PATCH` | `/api/v1/orders/*/status` | `ROLE_ADMIN` | Shipping and delivering |
| 13 | `GET` | `/api/v1/orders/admin` | `ROLE_ADMIN` | Every customer's orders |
| 14 | `POST` | `/api/v1/orders/*/refunds` | `ROLE_ADMIN` | Money goes back, so an admin decides |
| 15 | any | everything else | authenticated | Deny by default |

## 6. Is this record theirs?

A URL rule can't know who owns order 42; the service checks once the record is
loaded, through `AuthUtils` (no IDOR).

```mermaid
flowchart LR
    A(["GET /api/v1/orders/42<br/>as Ana, id 7"]) --> R1
    subgraph L1["Layer one: the URL, ShopConfig"]
        R1["anyRequest().authenticated()"]
    end
    subgraph L2["Layer two: the record, OrderService and AuthUtils"]
        F["findByIdWithItems(42)<br/>load the order first"] --> O{"requireSelfOrAdmin(9)<br/>order 42 is Ben's"}
    end
    R1 -->|signed in| F
    F -->|not found| N404["404 no such order"]
    O -->|"Ana is not 9 and not an admin"| N403["403 not theirs"]
    O -->|owner or admin| OK["200"]
    classDef no stroke:#d1242f,stroke-width:2px
    classDef yes stroke:#1a7f37,stroke-width:2px
    class N403 no
    class OK yes
```

The other way: query only the caller's rows with `authUtils.currentUserId()`, as
the wishlist, notifications and cart do.

Code: [`OrderService`](../shop-order/src/main/java/com/aryan/spring_security_demo/order/OrderService.java),
[`CartService`](../shop-cart/src/main/java/com/aryan/spring_security_demo/cart/CartService.java),
[`WishlistService`](../shop-wishlist/src/main/java/com/aryan/spring_security_demo/wishlist/WishlistService.java).

## 7. Refresh tokens

The access token lasts 15 minutes; the refresh token 7 days, stored only as its
SHA-256, and spent on every refresh.

```mermaid
stateDiagram-v2
    state "reuse detected" as REUSE
    [*] --> ACTIVE: issueFor, at login or password change
    ACTIVE --> REVOKED: rotate, POST /auth/refresh (a new ACTIVE token is issued)
    ACTIVE --> REVOKED: logout, POST /auth/logout
    ACTIVE --> EXPIRED: 7 days pass
    EXPIRED --> [*]: a refresh answers 401
    REVOKED --> REUSE: presented again
    REUSE --> [*]: revokeAllForUser ends every session, 401
    ACTIVE --> DELETED: password change, endAllSessions
    DELETED --> [*]
```

- A rotated token coming back means two parties hold it: every session of the user
  ends (`@Transactional(noRollbackFor = InvalidRefreshTokenException.class)`).
- In the browser, `client.ts` refreshes once on a `401`, shared between requests
  and tabs, then replays the request.

Code: [`RefreshTokenService.rotate`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/RefreshTokenService.java),
[`client.ts`](../frontend/src/api/client.ts).

## 8. Rate limiting

`RateLimitFilter` runs before authentication, only for `/api/v1/auth/**`: a token
bucket per client address, 5 tokens, refilled at 5 a minute.

| Time | Tokens before | Result | Tokens after |
|---|---|---|---|
| 0 s | 5.00 | allowed | 4.00 |
| 1 s | 4.08 | allowed | 3.08 |
| 2 s | 3.17 | allowed | 2.17 |
| 3 s | 2.25 | allowed | 1.25 |
| 4 s | 1.33 | allowed | 0.33 |
| 5 s | 0.42 | `429`, `Retry-After: 7` | 0.42 |
| 12 s | 1.00 | allowed | 0.00 |

The address is `request.getRemoteAddr()`; in production
`SERVER_FORWARD_HEADERS_STRATEGY=native` takes it from Caddy (README, "Security
model").

Code: [`RateLimitFilter`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/ratelimit/RateLimitFilter.java),
[`TokenBucket`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/ratelimit/TokenBucket.java).

## 9. Passwords

- BCrypt, through the one `PasswordEncoder` bean; never logged or returned; hashed
  before any transaction opens; at most 72 bytes.
- Changing one (`PUT /api/v1/auth/password`) checks the current password, saves the
  new hash only if the stored one hasn't changed meanwhile, and ends every other
  session.

Code: [`UserService`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/UserService.java),
[`UserAccountWriter`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/UserAccountWriter.java).

## 10. Callers without a user

Prometheus and the payment provider can't sign in for a JWT; each is authenticated
another way.

```mermaid
flowchart LR
    Q(["a request"]) --> FCP["FilterChainProxy<br/>runs the first chain whose matcher fits"]
    FCP -->|"/actuator/prometheus"| PC["prometheusScrapeFilterChain, @Order(1)<br/>HTTP Basic, only the scrape account"]
    FCP -->|anything else| SC["securityFilterChain, ShopConfig<br/>JWTs and the access map"]
```

```mermaid
flowchart LR
    P["permitAll()<br/>no JWT for this URL"] --> S1{"Payment-Signature<br/>t=..., v1=... present and parsable?"}
    S1 -->|yes| S2{"timestamp within<br/>5 minutes of now?"}
    S2 -->|yes| S3{"HMAC-SHA256 of t.body matches?<br/>compared with MessageDigest.isEqual"}
    S3 -->|yes| S4{"an event with<br/>an id and a type?"}
    S4 -->|yes| OK["processed once<br/>its event id is unique, a repeat is DUPLICATE"]
    S1 -->|no| U401["401 InvalidWebhookSignatureException"]
    S2 -->|no| U401
    S3 -->|no| U401
    S4 -->|no| B400["400 InvalidWebhookException"]
    classDef no stroke:#d1242f,stroke-width:2px
    classDef yes stroke:#1a7f37,stroke-width:2px
    class U401 no
    class OK yes
```

Code: [`PrometheusScrapeSecurityConfig`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/config/PrometheusScrapeSecurityConfig.java),
[`WebhookSignature.verify`](../shop-payment/src/main/java/com/aryan/spring_security_demo/payment/WebhookSignature.java),
[`PaymentWebhookService.receive`](../shop-payment/src/main/java/com/aryan/spring_security_demo/payment/PaymentWebhookService.java).

## 11. Sessions, CSRF, CORS and headers

| | |
|---|---|
| Sessions | `STATELESS`: no `HttpSession` |
| CSRF | Off: the credential is the `Authorization` header, which a browser never adds by itself |
| Tokens in the browser | `localStorage` (`tokenStore.ts`); React escapes everything it renders, no raw HTML |
| CORS | Only origins in `app.cors.allowed-origins`, never with credentials (`CorsConfig`) |
| Caddy | TLS, HTTP redirect, no `TRACE`, HSTS, `nosniff`, `Referrer-Policy`, no `Server` header ([`deploy/Caddyfile`](../deploy/Caddyfile)) |

## 12. Status codes and who sends them

| Code | Means here | Written by |
|---|---|---|
| `400` | Invalid body, a firewall-rejected request, or a webhook body that isn't an event | `GlobalExceptionHandler` |
| `401` | No valid JWT on a secured URL | `JwtEntryPoint` |
| `401` | Wrong email or password; a bad refresh token or webhook signature | `GlobalExceptionHandler` |
| `403` | Signed in, but the URL needs `ROLE_ADMIN` | `ApiAccessDeniedHandler` |
| `403` | The record belongs to someone else | `AuthUtils`, then `GlobalExceptionHandler` |
| `404` | No such record (looked up before ownership) | `GlobalExceptionHandler` |
| `429` | Too many `/auth` requests, with `Retry-After` | `RateLimitFilter` |

## 13. Key decisions

| Decision | Why | Rejected alternative |
|---|---|---|
| Every URL rule in one `SecurityFilterChain`, admin rules as `hasAuthority("ROLE_ADMIN")` | The access map reads in one place | `@PreAuthorize` on each controller |
| Deny by default | A new endpoint is never public by accident | Listing the protected paths |
| Ownership checked in the service, or queries scoped to `currentUserId()` | A URL rule can't know who owns a record | Trusting a client-sent id |
| The user reloaded on every request, the token bound to the account's id | Removals take effect at once | Trusting the token's roles until expiry |
| 15-minute access token, 7-day rotating refresh token stored as SHA-256 | Short-lived leaks, revocable sessions, reuse noticed | Long-lived tokens; raw refresh tokens |
| `Authorization` header, `STATELESS`, CSRF off | No cookie, no CSRF, no session store | Cookie sessions with CSRF tokens |
| `/auth/**` rate-limited per address, before authentication | Guessing is slowed before any password work | No limit |
| Filter-chain errors written as RFC 7807 by the filters | Every error looks the same | Spring's default error pages |
| RS256 with a published public key | Services can check tokens without being able to make them | HMAC with a shared secret |
| Spring's resource-server support | Parsing and validation are Spring's, not hand-written | A custom token filter |

## 14. Signing keys and the published public keys

```mermaid
flowchart LR
    ENV["JWT_SIGNING_KEY<br/>RSA private key, from the environment"] --> K["JwtKeys<br/>kid = the key's thumbprint (RFC 7638)"]
    PREV["JWT_PREVIOUS_PUBLIC_KEYS<br/>old public keys, while a key is replaced"] --> K
    K -->|private key| ENC["JwtEncoder<br/>signs RS256, kid in the header"]
    K -->|public keys| DEC["JwtDecoder<br/>RS256 only, picks the key by kid"]
    K -->|public keys| JW["GET /api/v1/.well-known/jwks.json<br/>JWK Set, cached 5 minutes"]
    JW -.->|"fetched by"| RS["another service<br/>verifies tokens, can't make them"]
```

- `JWT_SIGNING_KEY`: an RSA private key (2048 bits or more, PKCS#8 DER, Base64);
  the app won't start without a usable one. Dev and test may generate a throwaway
  key at startup, so no private key is ever committed.
- Replacing it: set the new key, keep the old public key in
  `JWT_PREVIOUS_PUBLIC_KEYS` for one access-token lifetime (15 minutes), then empty it.

Code: [`JwtKeys`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/jwt/JwtKeys.java),
[`JwtConfig`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/jwt/JwtConfig.java),
[`JwksController`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/JwksController.java).

## 15. Reading the code in order

1. [`ShopConfig`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/config/ShopConfig.java): the filter chain, the access map, the beans.
2. [`JwtConfig`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/jwt/JwtConfig.java), [`JwtKeys`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/jwt/JwtKeys.java), [`JwtUtils`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/jwt/JwtUtils.java), [`ShopJwtAuthenticationConverter`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/jwt/ShopJwtAuthenticationConverter.java): tokens.
3. [`ShopUserDetailsService`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/user/ShopUserDetailsService.java), [`ShopUserDetails`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/user/ShopUserDetails.java): the user as Spring sees it.
4. [`AuthController`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/AuthController.java), [`RefreshTokenService`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/RefreshTokenService.java): login, refresh, logout, password change.
5. [`AuthUtils`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/AuthUtils.java), then `OrderService.getOrder`: ownership.
6. [`JwtEntryPoint`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/jwt/JwtEntryPoint.java), [`ApiAccessDeniedHandler`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/ApiAccessDeniedHandler.java), [`GlobalExceptionHandler`](../shop-common/src/main/java/com/aryan/spring_security_demo/common/exception/GlobalExceptionHandler.java): how each no is written.
7. [`RateLimitFilter`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/ratelimit/RateLimitFilter.java), [`TokenBucket`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/ratelimit/TokenBucket.java): a filter of your own.
8. [`CorsConfig`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/config/CorsConfig.java), [`PrometheusScrapeSecurityConfig`](../shop-identity/src/main/java/com/aryan/spring_security_demo/identity/security/config/PrometheusScrapeSecurityConfig.java), [`WebhookSignature`](../shop-payment/src/main/java/com/aryan/spring_security_demo/payment/WebhookSignature.java): the special cases.

The integration tests show each rule from the other side:
`./mvnw test -pl shop-app -am -Dgroups=integration` (Docker needed).
