# Stage 1: Build JAR using Maven with Eclipse Temurin 17 JDK
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B || mvn dependency:resolve -B

# Build application
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Production JRE runtime (Optimized for Cloud Run / Container Platforms)
FROM eclipse-temurin:17-jre
WORKDIR /app

# Non-root user according to security best practice
RUN groupadd -r aloute && useradd -r -g aloute aloute

COPY --from=build --chown=aloute:aloute /workspace/target/*.jar app.jar

USER aloute

# Cloud Run injects $PORT environment variable (default 8080)
ENV PORT=8080
# BL-01 fail-closed guard: refuse dev-profile startup in production images.
# The dev profile seeds accounts with well-known passwords (ProductionProfileGuard).
ENV ALOUTE_PRODUCTION_MODE=true
EXPOSE 8080

# Use exec to ensure PID 1 receives SIGTERM for graceful shutdown
ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT:-8080} -Djava.net.preferIPv4Stack=true -Dmail.smtp.ssl.protocols=TLSv1.2,TLSv1.3 -Dmail.smtps.ssl.protocols=TLSv1.2,TLSv1.3 -Dhttps.protocols=TLSv1.2,TLSv1.3 -Djava.security.egd=file:/dev/./urandom -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} -XX:+UseG1GC -XX:MaxRAMPercentage=75.0 ${JAVA_OPTS} -jar app.jar"]

