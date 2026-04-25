# ── Build stage ───────────────────────────────────────────────────────────────
# Context: project root (set in docker-compose.yml)
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace

# Copy parent POM + all child POMs so Maven can resolve the module hierarchy
COPY pom.xml .
COPY ai-gateway/pom.xml ai-gateway/
COPY auth-service/pom.xml auth-service/

RUN apk add --no-cache maven && mvn -q -B -pl ai-gateway dependency:go-offline

# Copy only this module's source — keeps the layer cache valid for the other module
COPY ai-gateway/src ai-gateway/src
RUN mvn -q -B -pl ai-gateway -DskipTests package

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S gateway && adduser -S gateway -G gateway

COPY --from=build /workspace/ai-gateway/target/ai-gateway-*.jar app.jar
RUN chown gateway:gateway app.jar
USER gateway

EXPOSE 8081

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
