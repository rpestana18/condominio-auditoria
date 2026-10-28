// Request id and log context shared by api, rag and mcp (ADR 0008). Technical only: no business rule, no new library
// (SLF4J comes with Spring Boot and grpc-api with the gRPC already used by the three services).
dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("io.grpc:grpc-api")

    testImplementation("io.grpc:grpc-inprocess")
    testImplementation("io.grpc:grpc-stub")
    // MDC needs a binding in the tests; the services get Logback from Spring Boot
    testRuntimeOnly("ch.qos.logback:logback-classic")
}
