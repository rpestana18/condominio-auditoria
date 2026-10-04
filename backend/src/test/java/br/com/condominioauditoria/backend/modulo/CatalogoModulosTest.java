package br.com.condominioauditoria.backend.modulo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.backend.modulo.CatalogoModulos.DefinicaoModulo;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/** O catálogo versionado (catalogo-modulos.yml) tem o Assistente desligado por padrão e é validado no boot (RF-10.1). */
class CatalogoModulosTest {

    @Test
    void arquivoDoCatalogoTemOAssistenteDesligadoPorPadrao() throws Exception {
        CatalogoModulos catalogo = carregar();

        assertThat(catalogo.versao()).isEqualTo(1);
        assertThat(catalogo.modulos()).extracting(DefinicaoModulo::codigo).containsExactly(Modulos.ASSISTENTE);
        DefinicaoModulo assistente = catalogo.exigir(Modulos.ASSISTENTE);
        assertThat(assistente.nome()).isEqualTo("Assistente");
        assertThat(assistente.ligadoPorPadrao()).isFalse();
        assertThat(assistente.dependeDe()).isEmpty();
        assertThat(assistente.inclui()).anySatisfy(i -> assertThat(i).contains("Indexação"))
                .anySatisfy(i -> assertThat(i).contains("Busca nos documentos"))
                .anySatisfy(i -> assertThat(i).contains("buscar_documentos"))
                .anySatisfy(i -> assertThat(i).contains("chat"));
    }

    @Test
    void codigoForaDoCatalogoEhDesconhecido() {
        var catalogo = new CatalogoModulos(1, List.of(modulo("ASSISTENTE", List.of())));

        assertThat(catalogo.buscar("RELATORIOS")).isEmpty();
        assertThatThrownBy(() -> catalogo.exigir("RELATORIOS")).isInstanceOf(ModuloDesconhecidoException.class);
    }

    @Test
    void catalogoInvalidoImpedeOBoot() {
        assertThatThrownBy(() -> new CatalogoModulos(0, List.of())).hasMessageContaining("versao");
        assertThatThrownBy(() -> new CatalogoModulos(1, List.of(modulo("A1", List.of()), modulo("A1", List.of()))))
                .hasMessageContaining("repetido");
        assertThatThrownBy(() -> new CatalogoModulos(1, List.of(modulo("A1", List.of("B2")))))
                .hasMessageContaining("depende de B2");
        assertThatThrownBy(() -> modulo("minusculo", List.of())).hasMessageContaining("Código de módulo inválido");
    }

    @Test
    void moduloNovoEhSoUmaEntradaNoCatalogo() {
        var catalogo = new CatalogoModulos(2, List.of(modulo("ASSISTENTE", List.of()),
                modulo("RELATORIOS", List.of("ASSISTENTE"))));

        assertThat(catalogo.exigir("RELATORIOS").dependeDe()).containsExactly("ASSISTENTE");
    }

    static CatalogoModulos carregar() throws Exception {
        var ambiente = new StandardEnvironment();
        new YamlPropertySourceLoader().load("catalogo", new ClassPathResource("catalogo-modulos.yml"))
                .forEach(ambiente.getPropertySources()::addLast);
        return new Binder(ConfigurationPropertySources.get(ambiente))
                .bind("catalogo-modulos", CatalogoModulos.class).get();
    }

    private static DefinicaoModulo modulo(String codigo, List<String> dependeDe) {
        return new DefinicaoModulo(codigo, "Nome " + codigo, "descrição", List.of("algo"), dependeDe, false);
    }
}
