// Signal simulator — a CLI that replays scripted scenarios (cold-start, proxy,
// switch, camera-off, relay) as timed video/audio signals + meeting events to
// Kafka, so the whole M2 data plane can be exercised end-to-end without any ML.
plugins {
    application
}

dependencies {
    implementation(project(":libs:contracts"))
    implementation(project(":libs:common-kafka"))
    implementation("org.apache.kafka:kafka-clients:${libs.versions.kafka.get()}")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.13")
}

application {
    mainClass.set("com.sherlock.simulator.SimulatorMain")
}
