plugins {
    id("org.springframework.boot")
}

tasks.named<Jar>("jar") { enabled = false }

dependencies {
    implementation(project(":libs:armazenamento"))
    implementation(project(":libs:contrato-grpc"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation(libs.json.schema.validator)
    implementation(libs.grpc.netty.shaded)
    // Exportação do previsto × realizado (RF-03.1.14): PDF por Thymeleaf + OpenHTMLtoPDF, Excel por Apache POI.
    // O PDFBox, que o OpenHTMLtoPDF já traz, é usado nos testes para ler o texto do PDF gerado.
    implementation(libs.spring.boot.starter.thymeleaf)
    implementation(libs.openhtmltopdf.pdfbox)
    implementation(libs.poi.ooxml)
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.grpc.inprocess)
}

// Os contratos (JSON Schema das mensagens) entram no classpath a partir de contracts/
sourceSets.main { resources.srcDir(rootProject.file("contracts")) }
