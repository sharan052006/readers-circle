# Multi-stage build for Reader's Circle Spring Boot backend
# Stage 1: Build stage
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy pom.xml and source code
COPY pom.xml .
COPY src ./src

# Build production JAR without tests (tests are validated prior to build)
RUN mvn clean package -DskipTests -B

# Stage 2: Lightweight runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Run as a non-privileged user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Pre-create upload and data directories with proper non-root ownership and permissions
RUN mkdir -p /app/uploads/gallery /app/data /tmp/readers-circle/uploads/gallery && \
    chown -R appuser:appgroup /app /tmp/readers-circle && \
    chmod -R 775 /app /tmp/readers-circle

# Copy executable jar from builder stage
COPY --from=builder /app/target/readers-circle-0.0.1-SNAPSHOT.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser

# Render assigns PORT dynamically (defaults to 8080)
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
