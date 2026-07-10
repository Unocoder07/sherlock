plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

// Spring Boot's BOM pins Testcontainers to a docker-java too old for recent
// Docker Engine; override to the catalog version (see meeting-service for the
// full rationale and the Windows DOCKER_HOST note below).
extra["testcontainers.version"] = libs.versions.testcontainers.get()

dependencies {
    implementation(project(":libs:contracts"))
    implementation(project(":libs:common-kafka"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.kafka)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation("org.flywaydb:flyway-database-postgresql:10.17.3")

    testImplementation(libs.spring.boot.starter)
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.3.4")
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.testcontainers.kafka)
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("confidence-engine.jar")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // On Docker Desktop for Windows the active context uses a non-default named
    // pipe; point Testcontainers at the Linux-engine pipe (no-op on CI/Linux).
    // The Testcontainers integration test runs green on Linux/CI; on this Windows
    // box the docker-java npipe transport can't negotiate with Docker Engine 29,
    // so M2 is verified end-to-end via `docker compose` instead.
    if (System.getProperty("os.name").startsWith("Windows")) {
        environment("DOCKER_HOST", "npipe:////./pipe/dockerDesktopLinuxEngine")
    }
}
