plugins {
    id("org.springframework.boot")
}

tasks.named<Jar>("jar") { enabled = false }

dependencies {
    implementation(project(":libs:armazenamento"))

    // webmvc só para o /actuator/health; a entrada de trabalho é a fila
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("tools.jackson.core:jackson-databind")
    implementation(libs.json.schema.validator)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// Os contratos (leitor e mensagens) entram no classpath a partir de contracts/
sourceSets.main { resources.srcDir(rootProject.file("contracts")) }
