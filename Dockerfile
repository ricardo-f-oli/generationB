# ---- Build stage -------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Dependencies are their own layer so a source change does not re-download them.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# Q-E29: tests run in the image build, so a broken commit cannot produce a deployable jar.
# Pass --build-arg SKIP_TESTS=true only for an emergency hotfix.
#
# `package` runs surefire only — the unit suite. It deliberately does NOT run the integration
# suite: those need Testcontainers, and a Docker build stage has no Docker daemon of its own.
# Attempting them here produced five classes erroring with NoClassDefFoundError, which reads
# like a compilation failure and is not one.
#
# The integration suite runs in CI under `mvn verify`, where a daemon exists, and CI asserts
# that it actually executed.
ARG SKIP_TESTS=false
RUN if [ "$SKIP_TESTS" = "true" ]; then \
      mvn -B clean package -DskipTests; \
    else \
      mvn -B clean package; \
    fi

# ---- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Q-E29: do not run as root.
RUN addgroup -S app && adduser -S -G app app

# The local storage adapter writes here. /app is owned by root, so without this the container
# dies at startup with AccessDeniedException the moment STORAGE_PROVIDER=local — which is the
# documented fallback and what the CI smoke test uses. Production overrides to S3, so this is a
# safety net rather than the normal path.
RUN mkdir -p /app/data/uploads && chown -R app:app /app/data

COPY --from=builder --chown=app:app /app/target/generationb-0.0.1-SNAPSHOT.jar app.jar

USER app
EXPOSE 8080

# Render's free tier is 512MB. MaxRAMPercentage keeps the heap inside the container
# limit instead of the JVM guessing from the host's memory.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/health/live || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
