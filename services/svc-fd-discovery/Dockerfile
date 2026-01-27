# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy Maven configuration
COPY pom.xml .

# Download dependencies
RUN mvn dependency:resolve

# Copy source code
COPY src ./src

# Build application
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Install curl for health checks
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Copy JAR from builder stage
COPY --from=builder /app/target/fd-discovery-1.0-SNAPSHOT.jar app.jar

# Create non-root user for security
RUN useradd -m -u 1000 appuser && chown -R appuser:appuser /app
USER appuser

# Default values can be overridden via --env-file
ENV SPRING_PROFILES_ACTIVE=dev \
    SERVER_PORT=8081

# Expose port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

# Run application
# Spring Boot reads environment variables from application.yml (DB_URL, CORS_ALLOWED_ORIGINS, etc.)
ENTRYPOINT ["java", "-jar", "app.jar"]
