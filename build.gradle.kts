import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    alias(libs.plugins.spring.boot) apply false
}

subprojects {
    if (buildFile.exists().not()) return@subprojects

    apply(plugin = "java-library")

    group = "br.com.condominioauditoria"
    version = "0.1.0"

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
    }

    repositories { mavenCentral() }

    dependencies {
        // Todas as versões vêm do BOM do Spring Boot (e do Spring AI quando entrar)
        "implementation"(platform(SpringBootPlugin.BOM_COORDINATES))
        "testImplementation"(platform(SpringBootPlugin.BOM_COORDINATES))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"("org.assertj:assertj-core")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // Testes com o PDF real ficam em data/golden/privado (fora do git)
        systemProperty("golden.dir", rootProject.file("data/golden").absolutePath)
    }
}
