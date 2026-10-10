package br.com.condominioauditoria.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.rag.config.properties.AiProperties;
import br.com.condominioauditoria.rag.config.properties.AiProperties.AiFunction;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * The catalog is configuration, not code: this test reads the real application.yml and checks the initial catalog
 * agreed with the api (ADR 0003, Decision 1; delivery 3). Changing a model id or a price here breaks the test on
 * purpose: the api uses these values for the estimated cost of the usage report.
 */
class ConfiguredCatalogTest {

    private final ProviderCatalog catalog = new ProviderCatalog(fromApplicationYml());

    @Test
    void anthropicIsForAnswersWithSonnetDefaultAndHaikuCheaper() {
        var anthropic = catalog.byCode("anthropic").orElseThrow();

        assertThat(anthropic.type()).isEqualTo("anthropic");
        assertThat(anthropic.function()).isEqualTo(AiFunction.ANSWERS);
        assertThat(anthropic.local()).isFalse();
        assertThat(anthropic.requiresKey()).isTrue();
        assertThat(anthropic.dimension()).isZero();
        assertThat(anthropic.models()).extracting("id", "isDefault", "inputPricePerMillionUsd",
                "outputPricePerMillionUsd")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("claude-sonnet-5-5", true, "2.00", "10.00"),
                        org.assertj.core.groups.Tuple.tuple("claude-haiku-4-5", false, "1.00", "5.00"));
    }

    @Test
    void localOllamaIsForEmbeddingsWithBgeM3At1024DimensionsAndNoCost() {
        var ollama = catalog.byCode("ollama-local").orElseThrow();

        assertThat(ollama.type()).isEqualTo("ollama");
        assertThat(ollama.function()).isEqualTo(AiFunction.EMBEDDINGS);
        assertThat(ollama.local()).isTrue();
        assertThat(ollama.requiresKey()).isFalse();
        assertThat(ollama.dimension()).isEqualTo(1024);
        assertThat(ollama.models()).singleElement().satisfies(m -> {
            assertThat(m.id()).isEqualTo("bge-m3");
            assertThat(m.isDefault()).isTrue();
            assertThat(m.inputPricePerMillionUsd()).isEqualTo("0");
        });
    }

    @Test
    void answerModelRejectsEmbeddingsProviderAndModelNotInCatalog() {
        assertThat(catalog.answerModel("anthropic", "claude-haiku-4-5").name())
                .isEqualTo("Claude Haiku 4.5");

        assertThatThrownBy(() -> catalog.answerModel("ollama-local", "bge-m3"))
                .isInstanceOf(ProviderCatalog.ModelNotInCatalogException.class)
                .hasMessageContaining("não serve para redigir respostas");
        assertThatThrownBy(() -> catalog.answerModel("anthropic", "claude-outro"))
                .hasMessageContaining("não está no catálogo do provedor");
        assertThatThrownBy(() -> catalog.answerModel("openai", "gpt"))
                .hasMessageContaining("não está no catálogo");
    }

    private static AiProperties fromApplicationYml() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();
        // Properties.getProperty() returns null for a value that is not a String (true, 1024): here the object counts
        // as it came
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        properties.forEach((key, value) -> map.put(String.valueOf(key), value));
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("application.yml", map));
        return Binder.get(environment).bind("condominio.ai", AiProperties.class).get();
    }
}
