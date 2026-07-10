plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

// Explanation Engine (M4): consumes confidence.updates, renders the numeric
// contribution breakdown into English reasons, and republishes an EnrichedVerdict
// on verdict.enriched for the WS gateway. Stateless — no JPA/Redis/Flyway. It
// reuses the shared contracts (generated protos) + common-kafka (envelope codec,
// dedupe, publisher).
dependencies {
    implementation(project(":libs:contracts"))
    implementation(project(":libs:common-kafka"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.kafka)

    testImplementation(libs.spring.boot.starter)
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.3.4")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("explanation-engine.jar")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
