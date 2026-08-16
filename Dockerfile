# ---- Build stage -------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Dependencies are their own layer so a source change does not re-download them.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# Q-E29: tests run in the image build, so a broken commit cannot produce a deployable jar.
# Pass --build-arg SKIP_TESTS=true only for an emergency hotfix.
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

COPY --from=builder --chown=app:app /app/target/generationb-0.0.1-SNAPSHOT.jar app.jar

USER app
EXPOSE 8080

# Render's free tier is 512MB. MaxRAMPercentage keeps the heap inside the container
# limit instead of the JVM guessing from the host's memory.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/health/live || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
