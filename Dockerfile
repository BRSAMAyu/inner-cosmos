# Inner Cosmos — Production Dockerfile
# Multi-stage build for minimal image size

FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Use Maven Central cache if available
RUN --mount=type=cache,target=/root/.m2 \
    /root/.m2/bin/mvn dependency:go-offline -B
RUN /root/.m2/bin/mvn package -DskipTests -q

# Runtime image — JRE only (not full JDK)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1001 appgroup && adduser -u 1001 -G appgroup -D appuser

# Create log directory
RUN mkdir -p /var/log/inner-cosmos && chown appuser:appgroup /var/log/inner-cosmos

# Copy built artifact
COPY --from=builder /app/target/*.jar app.jar

# Switch to non-root user
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

# JVM tuning for container environment
# Container-aware memory settings (use cgroup limits)
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.backgroundpreinitializer.ignore=true"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]