dependencies {
    api(project(":backend:domain"))
    implementation("tools.jackson.core:jackson-databind")
    implementation(libs.json.schema.validator)
}

// O contrato JSON Schema do leitor entra no classpath a partir de contracts/
sourceSets.main { resources.srcDir(rootProject.file("contracts")) }
