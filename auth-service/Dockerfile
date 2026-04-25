# ── Build stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace

COPY pom.xml .
RUN apk add --no-cache maven && mvn -q -B dependency:go-offline

COPY src src
RUN mvn -q -B package -DskipTests

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S auth && adduser -S auth -G auth

COPY --from=build /workspace/target/auth-service-*.jar app.jar
RUN chown auth:auth app.jar
USER auth

EXPOSE 9000

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
