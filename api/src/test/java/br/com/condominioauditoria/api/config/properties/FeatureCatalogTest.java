package br.com.condominioauditoria.api.config.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.api.config.properties.FeatureCatalog.FeatureDefinition;
import br.com.condominioauditoria.api.exception.UnknownFeatureException;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * The versioned catalog (catalogo-modulos.yml) has the Assistant disabled by default and is validated at startup
 * (RF-10.1).
 */
public class FeatureCatalogTest {

    @Test
    void catalogFileHasTheAssistantDisabledByDefault() throws Exception {
        FeatureCatalog catalog = load();

        assertThat(catalog.version()).isEqualTo(1);
        assertThat(catalog.features()).extracting(FeatureDefinition::code).containsExactly(FeatureService.ASSISTANT);
        FeatureDefinition assistant = catalog.require(FeatureService.ASSISTANT);
        assertThat(assistant.name()).isEqualTo("Assistente");
        assertThat(assistant.enabledByDefault()).isFalse();
        assertThat(assistant.dependsOn()).isEmpty();
        assertThat(assistant.includes()).anySatisfy(i -> assertThat(i).contains("Indexação"))
                .anySatisfy(i -> assertThat(i).contains("Busca nos documentos"))
                .anySatisfy(i -> assertThat(i).contains("buscar_documentos"))
                .anySatisfy(i -> assertThat(i).contains("chat"));
    }

    @Test
    void codeOutsideTheCatalogIsUnknown() {
        var catalog = new FeatureCatalog(1, List.of(feature("ASSISTANT", List.of())));

        assertThat(catalog.find("RELATORIOS")).isEmpty();
        assertThatThrownBy(() -> catalog.require("RELATORIOS")).isInstanceOf(UnknownFeatureException.class);
    }

    @Test
    void invalidCatalogPreventsStartup() {
        assertThatThrownBy(() -> new FeatureCatalog(0, List.of())).hasMessageContaining("versao");
        assertThatThrownBy(() -> new FeatureCatalog(1, List.of(feature("A1", List.of()), feature("A1", List.of()))))
                .hasMessageContaining("repetido");
        assertThatThrownBy(() -> new FeatureCatalog(1, List.of(feature("A1", List.of("B2")))))
                .hasMessageContaining("depende de B2");
        assertThatThrownBy(() -> feature("minusculo", List.of())).hasMessageContaining("Código de módulo inválido");
    }

    @Test
    void newFeatureIsJustACatalogEntry() {
        var catalog = new FeatureCatalog(2, List.of(feature("ASSISTANT", List.of()),
                feature("RELATORIOS", List.of("ASSISTANT"))));

        assertThat(catalog.require("RELATORIOS").dependsOn()).containsExactly("ASSISTANT");
    }

    public static FeatureCatalog load() throws Exception {
        var environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("catalogo", new ClassPathResource("catalogo-modulos.yml"))
                .forEach(environment.getPropertySources()::addLast);
        return new Binder(ConfigurationPropertySources.get(environment))
                .bind("catalogo-modulos", FeatureCatalog.class).get();
    }

    private static FeatureDefinition feature(String code, List<String> dependsOn) {
        return new FeatureDefinition(code, "Nome " + code, "descrição", List.of("algo"), dependsOn, false);
    }
}
