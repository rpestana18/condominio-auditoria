package br.com.condominioauditoria.rag.assistente.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.rag.assistente.catalogo.PropriedadesIa.Uso;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * O catálogo é configuração, não código: este teste lê o application.yml de verdade e confere o catálogo inicial
 * combinado com o backend (ADR 0003, Decisão 1; entrega 3). Mudar um id de modelo ou um preço aqui quebra o teste de
 * propósito: o backend usa estes valores para o custo estimado do relatório de uso.
 */
class CatalogoConfiguradoTest {

    private final CatalogoProvedores catalogo = new CatalogoProvedores(doApplicationYml());

    @Test
    void anthropicEDeRespostasComSonnetPadraoEHaikuMaisBarato() {
        var anthropic = catalogo.porCodigo("anthropic").orElseThrow();

        assertThat(anthropic.tipo()).isEqualTo("anthropic");
        assertThat(anthropic.uso()).isEqualTo(Uso.RESPOSTAS);
        assertThat(anthropic.local()).isFalse();
        assertThat(anthropic.precisaChave()).isTrue();
        assertThat(anthropic.dimensao()).isZero();
        assertThat(anthropic.modelos()).extracting("id", "padrao", "precoEntradaMilhaoUsd", "precoSaidaMilhaoUsd")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("claude-sonnet-5-5", true, "2.00", "10.00"),
                        org.assertj.core.groups.Tuple.tuple("claude-haiku-4-5", false, "1.00", "5.00"));
    }

    @Test
    void ollamaLocalEDeEmbeddingsComBgeM3De1024DimensoesESemCusto() {
        var ollama = catalogo.porCodigo("ollama-local").orElseThrow();

        assertThat(ollama.tipo()).isEqualTo("ollama");
        assertThat(ollama.uso()).isEqualTo(Uso.EMBEDDINGS);
        assertThat(ollama.local()).isTrue();
        assertThat(ollama.precisaChave()).isFalse();
        assertThat(ollama.dimensao()).isEqualTo(1024);
        assertThat(ollama.modelos()).singleElement().satisfies(m -> {
            assertThat(m.id()).isEqualTo("bge-m3");
            assertThat(m.padrao()).isTrue();
            assertThat(m.precoEntradaMilhaoUsd()).isEqualTo("0");
        });
    }

    @Test
    void modeloDeRespostasRecusaProvedorDeEmbeddingsEModeloForaDoCatalogo() {
        assertThat(catalogo.modeloDeRespostas("anthropic", "claude-haiku-4-5").nome())
                .isEqualTo("Claude Haiku 4.5");

        assertThatThrownBy(() -> catalogo.modeloDeRespostas("ollama-local", "bge-m3"))
                .isInstanceOf(CatalogoProvedores.ModeloForaDoCatalogoException.class)
                .hasMessageContaining("não serve para redigir respostas");
        assertThatThrownBy(() -> catalogo.modeloDeRespostas("anthropic", "claude-outro"))
                .hasMessageContaining("não está no catálogo do provedor");
        assertThatThrownBy(() -> catalogo.modeloDeRespostas("openai", "gpt"))
                .hasMessageContaining("não está no catálogo");
    }

    private static PropriedadesIa doApplicationYml() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties propriedades = yaml.getObject();
        // Properties.getProperty() devolve null para valor que não é String (true, 1024): aqui vale o objeto como veio
        java.util.Map<String, Object> mapa = new java.util.LinkedHashMap<>();
        propriedades.forEach((chave, valor) -> mapa.put(String.valueOf(chave), valor));
        var ambiente = new StandardEnvironment();
        ambiente.getPropertySources().addFirst(new MapPropertySource("application.yml", mapa));
        return Binder.get(ambiente).bind("condominio.ia", PropriedadesIa.class).get();
    }
}
