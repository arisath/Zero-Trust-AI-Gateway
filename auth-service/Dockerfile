# ── Build stage ───────────────────────────────────────────────────────────────
# Context: project root (set in docker-compose.yml)
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace

# Copy parent POM + all child POMs so Maven can resolve the module hierarchy
COPY pom.xml .
COPY ai-gateway/pom.xml ai-gateway/
COPY auth-service/pom.xml auth-service/

RUN apk add --no-cache maven && mvn -q -B -pl auth-service dependency:go-offline

# Copy only this module's source — keeps the layer cache valid for the other module
COPY auth-service/src auth-service/src
RUN mvn -q -B -pl auth-service -DskipTests package

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S auth && adduser -S auth -G auth

COPY --from=build /workspace/auth-service/target/auth-service-*.jar app.jar
RUN chown auth:auth app.jar
USER auth

EXPOSE 9000

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
