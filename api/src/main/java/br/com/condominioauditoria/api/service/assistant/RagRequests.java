package br.com.condominioauditoria.api.service.assistant;

import br.com.condominioauditoria.api.dto.request.assistant.DocumentFiltersRequest;
import br.com.condominioauditoria.api.exception.InvalidRequestException;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.contratos.assistente.v1.FiltrosBusca;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Common conversions of the question and the search to the assistant's gRPC contract. */
final class RagRequests {

    private RagRequests() {
    }

    /** Request filters for the rag; null = no filters. Inverted period = 400. */
    static FiltrosBusca filters(DocumentFiltersRequest f) {
        if (f == null) {
            return null;
        }
        if (f.startDate() != null && f.endDate() != null && f.startDate().isAfter(f.endDate())) {
            throw new InvalidRequestException("Data inicial (" + f.startDate() + ") depois da final (" + f.endDate()
                    + ")");
        }
        var builder = FiltrosBusca.newBuilder();
        orEmpty(f.categories()).stream().filter(Objects::nonNull).map(FileCategory::name).distinct()
                .forEach(builder::addCategorias);
        orEmpty(f.fileIds()).stream().filter(Objects::nonNull).map(UUID::toString).distinct()
                .forEach(builder::addArquivoIds);
        if (f.startDate() != null) {
            builder.setDataInicio(f.startDate().toString());
        }
        if (f.endDate() != null) {
            builder.setDataFim(f.endDate().toString());
        }
        return builder.build();
    }

    static <T> List<T> orEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }
}
