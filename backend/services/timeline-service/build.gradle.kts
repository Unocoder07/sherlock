plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

// Timeline Service (M4): consumes verdict.enriched, appends an immutable history
// of state transitions + score inflections to PostgreSQL, and republishes each
// new entry on timeline.events for live push. Read-optimized projection (doc 02
// §6) — JPA + Flyway, no Redis. Reuses shared contracts + common-kafka.
dependencies {
    implementation(project(":libs:contracts"))
    implementation(project(":libs:common-kafka"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.kafka)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation("org.flywaydb:flyway-database-postgresql:10.17.3")

    testImplementation(libs.spring.boot.starter)
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.3.4")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("timeline-service.jar")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
