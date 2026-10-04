package br.com.condominioauditoria.backend.modulo;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Catálogo de módulos contratáveis (RF-10.1), lido no boot de catalogo-modulos.yml. Catálogo inválido (sem versão,
 * código repetido, dependência fora do catálogo) impede o backend de subir.
 */
@ConfigurationProperties(prefix = "catalogo-modulos")
public record CatalogoModulos(int versao, List<DefinicaoModulo> modulos) {

    public CatalogoModulos {
        if (versao <= 0) {
            throw new IllegalArgumentException("catalogo-modulos.versao precisa ser maior que zero");
        }
        modulos = modulos == null ? List.of() : List.copyOf(modulos);
        Set<String> codigos = new HashSet<>();
        for (DefinicaoModulo m : modulos) {
            if (!codigos.add(m.codigo())) {
                throw new IllegalArgumentException("Módulo repetido no catálogo: " + m.codigo());
            }
        }
        for (DefinicaoModulo m : modulos) {
            for (String dependencia : m.dependeDe()) {
                if (!codigos.contains(dependencia)) {
                    throw new IllegalArgumentException(
                            "Módulo " + m.codigo() + " depende de " + dependencia + ", que não está no catálogo");
                }
            }
        }
    }

    /** Uma entrada do catálogo: código, nome, descrição, o que inclui, dependências e estado de um condomínio novo. */
    public record DefinicaoModulo(String codigo, String nome, String descricao, List<String> inclui,
            List<String> dependeDe, boolean ligadoPorPadrao) {

        public DefinicaoModulo {
            if (codigo == null || !codigo.matches("[A-Z][A-Z0-9_]{1,39}")) {
                throw new IllegalArgumentException("Código de módulo inválido no catálogo: " + codigo);
            }
            if (nome == null || nome.isBlank()) {
                throw new IllegalArgumentException("Módulo " + codigo + " sem nome no catálogo");
            }
            descricao = descricao == null ? "" : descricao.strip();
            inclui = inclui == null ? List.of() : List.copyOf(inclui);
            dependeDe = dependeDe == null ? List.of() : List.copyOf(dependeDe);
        }
    }

    public Optional<DefinicaoModulo> buscar(String codigo) {
        return modulos.stream().filter(m -> m.codigo().equals(codigo)).findFirst();
    }

    /** O módulo do catálogo; código desconhecido vira 404 na API. */
    public DefinicaoModulo exigir(String codigo) {
        return buscar(codigo).orElseThrow(() -> new ModuloDesconhecidoException(codigo));
    }
}
