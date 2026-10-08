// Generates the Java classes of the gRPC contract (contracts/grpc/*.proto). Backend (server), rag and mcp use this jar;
// no domain class is shared, only the contract.
plugins {
    id("com.google.protobuf")
}

dependencies {
    api(libs.grpc.stub)
    api(libs.grpc.protobuf)
    api(libs.protobuf.java)
}

sourceSets.main { proto.srcDir(rootProject.file("contracts/grpc")) }

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}" }
    plugins {
        create("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}" }
    }
    generateProtoTasks {
        all().configureEach { plugins { create("grpc") } }
    }
}
