plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

extra["testcontainers.version"] = libs.versions.testcontainers.get()

dependencies {
    implementation(project(":libs:contracts"))
    implementation(project(":libs:common-kafka"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.kafka)

    testImplementation(libs.spring.boot.starter)
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.3.4")
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.kafka)
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("evidence-fusion-service.jar")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Windows Docker Desktop uses a non-default named pipe (see meeting-service note).
    if (System.getProperty("os.name").startsWith("Windows")) {
        environment("DOCKER_HOST", "npipe:////./pipe/dockerDesktopLinuxEngine")
    }
}
