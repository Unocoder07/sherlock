# Multi-stage build. BUILD CONTEXT MUST BE THE REPO ROOT (shared backend/ + contracts/).
#   docker build -f backend/services/identity-anchor-service/Dockerfile -t identity-anchor-service .

FROM gradle:8.10.2-jdk21 AS build
WORKDIR /repo
COPY backend /repo/backend
COPY contracts /repo/contracts
WORKDIR /repo/backend
RUN gradle --no-daemon :services:identity-anchor-service:bootJar -x test

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r sherlock && useradd -r -g sherlock sherlock
COPY --from=build /repo/backend/services/identity-anchor-service/build/libs/identity-anchor-service.jar app.jar
USER sherlock
EXPOSE 8091
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
