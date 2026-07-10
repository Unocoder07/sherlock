plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

// Edge gateway (M3): consumes confidence.updates off Kafka and fans it out over
// STOMP-over-WebSocket to the browser. Stateless — no JPA/Redis/Flyway. It reuses
// the shared contracts (generated protos) + common-kafka (envelope codec, dedupe).
dependencies {
    implementation(project(":libs:contracts"))
    implementation(project(":libs:common-kafka"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.websocket)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.kafka)

    testImplementation(libs.spring.boot.starter)
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.3.4")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("edge-gateway.jar")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
