# Shopping Cart — Spring Security

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen)
![Spring Security](https://img.shields.io/badge/Spring%20Security-JWT-green)
![Database](https://img.shields.io/badge/MySQL-Hibernate-blue)
![API Docs](https://img.shields.io/badge/API%20Docs-Swagger%20UI-85EA2D)
![Build](https://img.shields.io/badge/build-Maven-red)

An e-commerce REST API built with **Spring Boot 4.1** and **Java 17**, featuring
JWT-based stateless authentication, role-based authorization, a product catalog,
cart, and order flow. Interactive API documentation is served by **Swagger UI**
(springdoc-openapi).

## Tech stack

| Concern     | Choice                                          |
|-------------|-------------------------------------------------|
| Framework   | Spring Boot 4.1.0 (Web MVC, Data JPA, Security) |
| Language    | Java 17                                         |
| Database    | MySQL (`mysql-connector-j`), Hibernate          |
| Auth        | JWT (jjwt 0.12.5), stateless, BCrypt            |
| Mapping     | ModelMapper 3.2.4                               |
| API docs    | springdoc-openapi 3.1.0 (OpenAPI 3 + Swagger UI)|
| Boilerplate | Lombok                                          |
| Build       | Maven wrapper (`./mvnw`)                         |

## Getting started

### Prerequisites
- JDK 17+
- MySQL running and reachable

### Configure
Copy the example config and point it at your database, then supply the required
environment variables:

```bash
cp src/main/resources/application.properties.example \
   src/main/resources/application.properties
```

| Variable      | Purpose                                            |
|---------------|----------------------------------------------------|
| `DB_URL`      | JDBC URL, e.g. `jdbc:mysql://localhost:3306/shop`  |
| `DB_USERNAME` | Database user                                       |
| `DB_PASSWORD` | Database password                                   |
| `JWT_SECRET`  | Base64 key, ≥256 bits — `openssl rand -base64 32`   |

### Run

```bash
./mvnw spring-boot:run
```

## API documentation

Once the app is running, springdoc-openapi exposes:

- **Swagger UI** — `http://localhost:8080/swagger-ui.html`
- **OpenAPI spec** — `http://localhost:8080/v3/api-docs`

Both sit outside the `api.prefix` and are reachable without authentication. A
global `bearerAuth` (JWT) scheme is declared, so click **Authorize** in Swagger
UI and paste the token from `POST /api/v1/auth/login` to call the secured
cart/order endpoints.

## Build & test

```bash
./mvnw clean compile   # compile
./mvnw test            # run tests
./mvnw clean package   # build the jar
```
