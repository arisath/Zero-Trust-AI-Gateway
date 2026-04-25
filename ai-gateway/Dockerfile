# ── Build stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace

# Cache dependency resolution separately from source compilation
COPY pom.xml .
RUN apk add --no-cache maven && mvn -q -B dependency:go-offline

COPY src src
RUN mvn -q -B package -DskipTests

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Run as non-root
RUN addgroup -S gateway && adduser -S gateway -G gateway

COPY --from=build /workspace/target/ai-gateway-*.jar app.jar
RUN chown gateway:gateway app.jar
USER gateway

EXPOSE 8081

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
