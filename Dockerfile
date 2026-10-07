# ===========================================================================
# Multi-stage build for the Shopping Cart API.
#
# Principles baked in here:
#   * NO configuration or secrets in the image. The jar carries only non-secret
#     defaults (application*.yml); DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET,
#     SPRING_PROFILES_ACTIVE, etc. are injected at RUNTIME via env vars / a
#     secrets manager. The same image runs unchanged in every environment.
#   * Pinned base images (never :latest) for reproducible, patchable builds.
#   * Runtime is a JRE only (no JDK, no Maven, no source) — smaller attack
#     surface and image size.
#   * Runs as an unprivileged user, never root.
# ===========================================================================

# --- Stage 1: build the jar with a full JDK -------------------------------
# Pinned to the 17 (jammy) line to match <java.version>17</java.version>. For
# fully reproducible builds, pin to a digest (eclipse-temurin:17-jdk-jammy@sha256:…).
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# Copy only what's needed to resolve dependencies first, so this layer is cached
# and re-downloaded only when a POM changes. go-offline fetches the third-party
# dependencies of every module; it skips the project's own modules, which are
# built from source below.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY shop-common/pom.xml shop-common/
COPY shop-identity/pom.xml shop-identity/
COPY shop-catalog/pom.xml shop-catalog/
COPY shop-cart/pom.xml shop-cart/
COPY shop-order/pom.xml shop-order/
COPY shop-notification/pom.xml shop-notification/
COPY shop-wishlist/pom.xml shop-wishlist/
COPY shop-app/pom.xml shop-app/
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Now the sources; tests run in CI, not in the image build. -pl shop-app -am
# builds the application and the modules it depends on.
COPY shop-common/src/ shop-common/src/
COPY shop-identity/src/ shop-identity/src/
COPY shop-catalog/src/ shop-catalog/src/
COPY shop-cart/src/ shop-cart/src/
COPY shop-order/src/ shop-order/src/
COPY shop-notification/src/ shop-notification/src/
COPY shop-wishlist/src/ shop-wishlist/src/
COPY shop-app/src/ shop-app/src/
RUN ./mvnw -B -pl shop-app -am clean package -DskipTests

# --- Stage 2: minimal runtime --------------------------------------------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Create an unprivileged user and group; the container must not run as root.
RUN groupadd --system spring && useradd --system --gid spring --home /app spring

# Copy just the fat jar from the build stage (Spring Boot repackages shop-app's
# runnable jar as shop-app/target/*.jar; the *.jar.original is not matched).
COPY --from=build /workspace/shop-app/target/*.jar app.jar

# Drop privileges.
USER spring:spring

EXPOSE 8080

# exec so the JVM is PID 1 and receives SIGTERM directly (Spring graceful
# shutdown). JAVA_OPTS is overridable at runtime (heap %, GC flags, …); the JVM
# is already container-aware and respects cgroup memory limits.
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar /app/app.jar"]
