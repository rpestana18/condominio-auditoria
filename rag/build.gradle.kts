plugins {
    id("org.springframework.boot")
}

tasks.named<Jar>("jar") { enabled = false }

dependencies {
    implementation(project(":libs:storage"))
    implementation(project(":libs:grpc-contract"))
    implementation(platform(libs.spring.ai.bom))

    // webmvc só para o /actuator/health; a entrada de trabalho é a fila (e o gRPC do assistente)
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("tools.jackson.core:jackson-databind")
    implementation(libs.json.schema.validator)

    // Índice dos documentos no schema rag (ADR 0003, Decisão 3): SQL próprio, sem JPA e sem PgVectorStore
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Embeddings locais pelo Ollama, modelo bge-m3 (ADR 0003, Decisão 2)
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")

    // Respostas do chat pelo Claude (ADR 0003, Decisão 1 A). O starter traz, de forma transitiva, o SDK oficial
    // com.anthropic:anthropic-java, que é o que o rag chama (um cliente por pergunta, com a chave do condomínio);
    // a autoconfiguração do ChatModel do Spring AI fica desligada (spring.ai.model.chat=none), porque ela exigiria
    // uma chave única no boot. Nenhuma biblioteca nova: só esta, aprovada na ADR.
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")

    // Servidor gRPC do assistente (contracts/grpc/assistente/v1)
    implementation(libs.grpc.netty.shaded)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.grpc.inprocess)
}

// Os contratos (leitor e mensagens) entram no classpath a partir de contracts/
sourceSets.main { resources.srcDir(rootProject.file("contracts")) }
