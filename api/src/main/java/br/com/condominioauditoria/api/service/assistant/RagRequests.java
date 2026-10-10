package br.com.condominioauditoria.api.service.assistant;

import br.com.condominioauditoria.api.dto.request.assistant.DocumentFiltersRequest;
import br.com.condominioauditoria.api.exception.InvalidRequestException;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.contracts.assistant.v2.SearchFilters;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Common conversions of the question and the search to the assistant's gRPC contract. */
final class RagRequests {

    private RagRequests() {
    }

    /** Request filters for the rag; null = no filters. Inverted period = 400. */
    static SearchFilters filters(DocumentFiltersRequest f) {
        if (f == null) {
            return null;
        }
        if (f.startDate() != null && f.endDate() != null && f.startDate().isAfter(f.endDate())) {
            throw new InvalidRequestException("Data inicial (" + f.startDate() + ") depois da final (" + f.endDate()
                    + ")");
        }
        var builder = SearchFilters.newBuilder();
        orEmpty(f.categories()).stream().filter(Objects::nonNull).map(FileCategory::name).distinct()
                .forEach(builder::addCategories);
        orEmpty(f.fileIds()).stream().filter(Objects::nonNull).map(UUID::toString).distinct()
                .forEach(builder::addFileIds);
        if (f.startDate() != null) {
            builder.setDateFrom(f.startDate().toString());
        }
        if (f.endDate() != null) {
            builder.setDateTo(f.endDate().toString());
        }
        return builder.build();
    }

    static <T> List<T> orEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }
}
