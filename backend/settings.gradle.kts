rootProject.name = "sherlock-backend"

// Gradle multi-module backend. Modules are added as milestones land:
//
//   libs/common-domain      shared value objects / base types (no framework deps)
//   libs/common-kafka       envelope (de)serialization, producer/consumer helpers
//   libs/common-web         REST/DTO/error-handling shared config
//   services/meeting-service            (M1)
//   services/edge-gateway               (M1 REST / M3 WS)
//   services/identity-anchor-service    (M2 mock / M6.5 real)
//   services/evidence-fusion-service    (M2)
//   services/confidence-engine          (M2)
//   services/timeline-service           (M4)
//   services/explanation-engine         (M4)
//   services/notification-service       (M4)
//
include(":libs:contracts")
include(":libs:common-kafka")
include(":services:meeting-service")
include(":services:confidence-engine")
include(":services:evidence-fusion-service")
include(":services:identity-anchor-service")
include(":services:edge-gateway")
include(":services:explanation-engine")
include(":services:timeline-service")
include(":services:notification-service")
include(":tools:signal-simulator")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    // gradle/libs.versions.toml is auto-imported as the `libs` catalog.
}
