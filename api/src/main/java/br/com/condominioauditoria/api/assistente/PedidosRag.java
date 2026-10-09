package br.com.condominioauditoria.api.assistente;

import br.com.condominioauditoria.api.assistente.DtosAssistente.FiltrosDocumentos;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.modulo.PedidoInvalidoException;
import br.com.condominioauditoria.contratos.assistente.v1.FiltrosBusca;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Conversões comuns da pergunta e da busca para o contrato gRPC do assistente. */
final class PedidosRag {

    private PedidosRag() {
    }

    /** Filtros do pedido para o rag; nulo = sem filtros. Período invertido = 400. */
    static FiltrosBusca filtros(FiltrosDocumentos f) {
        if (f == null) {
            return null;
        }
        if (f.dataInicio() != null && f.dataFim() != null && f.dataInicio().isAfter(f.dataFim())) {
            throw new PedidoInvalidoException("Data inicial (" + f.dataInicio() + ") depois da final (" + f.dataFim()
                    + ")");
        }
        var construtor = FiltrosBusca.newBuilder();
        lista(f.categorias()).stream().filter(Objects::nonNull).map(FileCategory::name).distinct()
                .forEach(construtor::addCategorias);
        lista(f.arquivoIds()).stream().filter(Objects::nonNull).map(UUID::toString).distinct()
                .forEach(construtor::addArquivoIds);
        if (f.dataInicio() != null) {
            construtor.setDataInicio(f.dataInicio().toString());
        }
        if (f.dataFim() != null) {
            construtor.setDataFim(f.dataFim().toString());
        }
        return construtor.build();
    }

    static <T> List<T> lista(List<T> valores) {
        return valores == null ? List.of() : valores;
    }
}
