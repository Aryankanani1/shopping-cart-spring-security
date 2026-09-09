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
# and re-downloaded only when the build files actually change.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Now the sources; tests run in CI, not in the image build.
COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# --- Stage 2: minimal runtime --------------------------------------------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Create an unprivileged user and group; the container must not run as root.
RUN groupadd --system spring && useradd --system --gid spring --home /app spring

# Copy just the fat jar from the build stage (Spring Boot repackages the runnable
# jar as target/*.jar; the *.jar.original is not matched).
COPY --from=build /workspace/target/*.jar app.jar

# Drop privileges.
USER spring:spring

EXPOSE 8080

# exec so the JVM is PID 1 and receives SIGTERM directly (Spring graceful
# shutdown). JAVA_OPTS is overridable at runtime (heap %, GC flags, …); the JVM
# is already container-aware and respects cgroup memory limits.
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar /app/app.jar"]
