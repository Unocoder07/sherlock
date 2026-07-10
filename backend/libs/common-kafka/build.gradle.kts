// Shared Kafka plumbing reused by every event-driven service and the simulator:
// the EventEnvelope codec, a thin EventPublisher over KafkaTemplate, and an
// idempotency guard. Only `contracts` is exposed as api — the Spring bits are
// compileOnly so plain consumers (e.g. the CLI simulator, which uses only the
// framework-free EnvelopeCodec) don't inherit Spring on their classpath. Each
// Spring Boot service already brings spring-kafka + autoconfigure itself.
plugins {
    `java-library`
}

val springBootBom = "org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}"

dependencies {
    api(project(":libs:contracts"))

    compileOnly(platform(springBootBom))
    compileOnly(libs.spring.kafka)
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")

    testImplementation(platform(springBootBom))
    testImplementation(libs.spring.kafka)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
