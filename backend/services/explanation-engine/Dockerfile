# Multi-stage build. BUILD CONTEXT MUST BE THE REPO ROOT so the shared
# backend/ (incl. libs/contracts + libs/common-kafka) and contracts/ are present.
#   docker build -f backend/services/explanation-engine/Dockerfile -t explanation-engine .

# ── build stage ──
FROM gradle:8.10.2-jdk21 AS build
WORKDIR /repo
COPY backend /repo/backend
COPY contracts /repo/contracts
WORKDIR /repo/backend
RUN gradle --no-daemon :services:explanation-engine:bootJar -x test

# ── runtime stage ──
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r sherlock && useradd -r -g sherlock sherlock
COPY --from=build /repo/backend/services/explanation-engine/build/libs/explanation-engine.jar app.jar
USER sherlock
EXPOSE 8095
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
