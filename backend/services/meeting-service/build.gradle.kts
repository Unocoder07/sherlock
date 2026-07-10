plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.protobuf)
}

// Spring Boot's BOM pins Testcontainers (1.19.8 for Boot 3.3.4), whose bundled
// docker-java is too old to negotiate with recent Docker Engine (returns HTTP 400
// on /info). Override the BOM property so we use the catalog version instead.
extra["testcontainers.version"] = libs.versions.testcontainers.get()

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.actuator)
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(libs.spring.kafka)
    implementation(libs.protobuf.java)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    // Flyway 10 needs the postgres-specific module on the classpath.
    implementation("org.flywaydb:flyway-database-postgresql:10.17.3")

    testImplementation(libs.spring.boot.starter) // brings spring-boot-starter-test transitively below
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.3.4")
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.testcontainers.kafka)
}

// ── Compile the SHARED contracts (contracts/proto) into Java here ──
// Single source of truth: the service speaks the exact schemas the bus uses.
protobuf {
    // The plugin generates the Java builtin by default; no task customization needed.
    protoc {
        artifact = "com.google.protobuf:protoc:4.28.2"
    }
}

sourceSets {
    main {
        proto {
            // repo-root/contracts/proto  (this module is backend/services/meeting-service)
            srcDir("../../../contracts/proto")
        }
    }
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("meeting-service.jar")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // On Docker Desktop for Windows the active context uses a non-default named
    // pipe; Testcontainers otherwise probes //./pipe/docker_engine and fails
    // discovery. Point it at the Linux-engine pipe (no-op on CI/Linux, where
    // DOCKER_HOST is unset or already correct).
    //
    // NOTE: Against Docker Engine 29 on Windows, the docker-java zerodep npipe
    // transport bundled by Testcontainers 1.20.2 cannot negotiate the API
    // version over the pipe (container create defaults to API 1.32, which the
    // engine rejects; pinning DOCKER_API_VERSION instead breaks the /info probe).
    // The Testcontainers integration test therefore runs green on Linux/CI, or
    // on Windows with Docker exposed over TCP. On this dev box the M1 exit
    // criterion is verified end-to-end via `docker compose` instead (see README).
    if (System.getProperty("os.name").startsWith("Windows")) {
        environment("DOCKER_HOST", "npipe:////./pipe/dockerDesktopLinuxEngine")
    }
}
