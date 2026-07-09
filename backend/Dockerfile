# ─── Stage 1: Build ───────────────────────────────────────────────────────────
# Full JDK + Maven to compile and package the application.
# Pinned to a specific Maven + Temurin tag so builds are reproducible.
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copy the POM first and resolve all dependencies before touching source.
# Docker caches this layer separately from the source layer, so a source-only
# change does not re-download the entire dependency graph.
COPY pom.xml .
RUN mvn dependency:go-offline --batch-mode --quiet

# Now copy source and build. This layer re-runs only when source files change.
COPY src ./src
RUN mvn package --batch-mode --quiet -DskipTests


# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
# Minimal JRE only — no JDK, no Maven, no source files enter the final image.
FROM eclipse-temurin:21-jre-jammy AS runtime

# Install curl for the Docker healthcheck.
# --no-install-recommends keeps the layer small; cleaning the apt cache keeps it clean.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Create a dedicated non-root system user.
# Running as root inside a container is poor security practice.
RUN groupadd --system icsr \
 && useradd --system --gid icsr --no-create-home icsr

WORKDIR /app

# Pull only the packaged fat jar from the build stage.
# The wildcard matches the versioned artifact name without hardcoding it.
COPY --from=build /workspace/target/*.jar app.jar

# Transfer ownership before switching user so the process can read the jar.
RUN chown icsr:icsr app.jar

USER icsr

EXPOSE 8080

# exec-form ENTRYPOINT: signals go directly to the JVM process, not a shell.
ENTRYPOINT ["java", "-jar", "app.jar"]
