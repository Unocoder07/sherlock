// Shared contracts module: compiles the single source-of-truth protobuf schemas
// in repo-root/contracts/proto into Java ONCE. Every service + tool depends on
// this instead of re-running protoc per module.
plugins {
    `java-library`
    alias(libs.plugins.protobuf)
}

dependencies {
    // Exposed to consumers: they use the generated message types + protobuf runtime.
    api(libs.protobuf.java)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.28.2"
    }
}

sourceSets {
    main {
        proto {
            // repo-root/contracts/proto  (this module is backend/libs/contracts)
            srcDir("../../../contracts/proto")
        }
    }
}
