// Gera as classes Java do contrato gRPC (contracts/grpc/*.proto). Backend (servidor) e mcp (cliente) usam este jar;
// nenhuma classe de domínio é compartilhada, só o contrato.
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
