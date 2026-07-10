# Multi-stage build. BUILD CONTEXT MUST BE THE REPO ROOT so the shared
# contracts/ (compiled into the service via protobuf) is available.
#   docker build -f backend/services/meeting-service/Dockerfile -t meeting-service .

# ── build stage ──
FROM gradle:8.10.2-jdk21 AS build
WORKDIR /repo
# Copy only what the build needs (backend sources + shared contracts).
COPY backend /repo/backend
COPY contracts /repo/contracts
WORKDIR /repo/backend
RUN gradle --no-daemon :services:meeting-service:bootJar -x test

# ── runtime stage ──
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
# curl for the container healthcheck; then a non-root runtime user.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r sherlock && useradd -r -g sherlock sherlock
COPY --from=build /repo/backend/services/meeting-service/build/libs/meeting-service.jar app.jar
USER sherlock
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
