# Stage 1: Build JAR using Maven with Eclipse Temurin 17 JDK
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build application
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Production JRE runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

# Non-root user according to security best practice
RUN groupadd -r aloute && useradd -r -g aloute aloute

COPY --from=build --chown=aloute:aloute /workspace/target/*.jar app.jar

USER aloute

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
