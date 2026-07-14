# Inner Cosmos — Production Dockerfile
# Multi-stage build for minimal image size.
# Builder uses the project's bundled Maven Wrapper (./mvnw) so the build inside
# the container matches `./mvnw` locally — no system Maven install required.

FROM eclipse-temurin:21-jdk-alpine@sha256:1ff763083f2993d57d0bf374ab10bb3e2cb873af6c13a04458ebbd3e0337dc76 AS builder
WORKDIR /app
# apk cache + .m2 cache via buildkit mounts (no cross-layer pollution)
RUN apk add --no-cache bash
COPY .mvn .mvn
COPY mvnw mvnw
COPY pom.xml pom.xml
# Pre-fetch deps (fail fast, better layer caching). javadoc/attachments stay off.
RUN ./mvnw -B -q -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

# Runtime image — JRE only (not full JDK)
FROM eclipse-temurin:21-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c
WORKDIR /app

# Pull fixed Alpine packages into the selected runtime base before release scanning.
RUN apk upgrade --no-cache

# Create non-root user for security
RUN addgroup -g 1001 appgroup && adduser -u 1001 -G appgroup -D appuser

# Create writable runtime directories (owned by non-root user). Production uses an
# external database; the local/dev smoke still needs its H2 data directory.
RUN mkdir -p /var/log/inner-cosmos /app/data \
    && chown -R appuser:appgroup /var/log/inner-cosmos /app/data

# Copy the fat-jar produced by spring-boot-maven-plugin
COPY --from=builder /app/target/*.jar app.jar

# Switch to non-root user
USER appuser

EXPOSE 8080

# JVM tuning for container environment (cgroup-aware memory + G1GC)
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.backgroundpreinitializer.ignore=true"

# Health check hitting the actuator health endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
