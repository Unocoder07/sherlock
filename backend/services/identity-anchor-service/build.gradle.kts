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
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("identity-anchor-service.jar")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    if (System.getProperty("os.name").startsWith("Windows")) {
        environment("DOCKER_HOST", "npipe:////./pipe/dockerDesktopLinuxEngine")
    }
}
