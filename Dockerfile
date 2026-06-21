# ============================================================
# Backend Dockerfile — multi-stage build
# ============================================================

# Stage 1: Build the JAR
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy Maven wrapper and POM first (layer-caching friendly)
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copy sources and build
COPY src ./src
RUN ./mvnw package -DskipTests -q

# ---------------------------------------------------------------
# Stage 2: Minimal runtime image
# ---------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Non-root user for security
RUN addgroup -S rummy && adduser -S rummy -G rummy
USER rummy

COPY --from=builder /app/target/*.jar app.jar

# JVM tuning for container awareness
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

