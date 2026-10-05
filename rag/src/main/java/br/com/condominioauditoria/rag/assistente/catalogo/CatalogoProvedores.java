package br.com.condominioauditoria.rag.assistente.catalogo;

import br.com.condominioauditoria.rag.assistente.catalogo.PropriedadesIa.ModeloIa;
import br.com.condominioauditoria.rag.assistente.catalogo.PropriedadesIa.ProvedorIa;
import br.com.condominioauditoria.rag.assistente.catalogo.PropriedadesIa.Uso;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Consultas ao catálogo de provedores (configuração), usadas pelo rpc ListarProvedores e por Perguntar. */
@Component
public class CatalogoProvedores {

    private final List<ProvedorIa> provedores;

    public CatalogoProvedores(PropriedadesIa propriedades) {
        this.provedores = propriedades.provedores() == null ? List.of() : List.copyOf(propriedades.provedores());
    }

    /** Na ordem do catálogo, como o contrato pede. */
    public List<ProvedorIa> provedores() {
        return provedores;
    }

    public Optional<ProvedorIa> porCodigo(String codigo) {
        return provedores.stream().filter(p -> p.codigo().equals(codigo)).findFirst();
    }

    /**
     * Provedor e modelo precisam existir no catálogo e o provedor precisa ser de respostas; qualquer outra coisa é
     * FAILED_PRECONDITION em Perguntar (contrato assistente.proto).
     */
    public ModeloIa modeloDeRespostas(String codigoProvedor, String idModelo) {
        ProvedorIa provedor = porCodigo(codigoProvedor).orElseThrow(() -> new ModeloForaDoCatalogoException(
                "provedor de IA \"" + codigoProvedor + "\" não está no catálogo deste rag"));
        if (provedor.uso() != Uso.RESPOSTAS) {
            throw new ModeloForaDoCatalogoException(
                    "o provedor \"" + codigoProvedor + "\" não serve para redigir respostas");
        }
        return provedor.modelos().stream().filter(m -> m.id().equals(idModelo)).findFirst()
                .orElseThrow(() -> new ModeloForaDoCatalogoException("modelo \"" + idModelo
                        + "\" não está no catálogo do provedor \"" + codigoProvedor + "\""));
    }

    /** Provedor ou modelo fora do catálogo, ou de uso diferente de RESPOSTAS. */
    public static class ModeloForaDoCatalogoException extends RuntimeException {
        public ModeloForaDoCatalogoException(String mensagem) {
            super(mensagem);
        }
    }
}
