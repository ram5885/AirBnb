# syntax=docker/dockerfile:1
# ---- build: compile and package the Spring Boot jar (tests run on the host with ./scripts/test-all.sh)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
# Dependencies first, so source changes don't re-download them (BuildKit cache keeps ~/.m2 between builds).
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B -q package -DskipTests \
    && cp target/review-service-*.jar /app.jar

# ---- run: small JRE image, non-root user
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app && mkdir -p /app/logs && chown app:app /app/logs
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
USER app
EXPOSE 8084
# app.log, access.log and audit.log are written here (compose mounts it to ./logs/<service>/ on the host)
ENV LOG_DIR=/app/logs \
    SERVER_PORT=8084 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 \
    CMD wget -qO- http://127.0.0.1:8084/actuator/health/readiness | grep -q '"UP"' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
