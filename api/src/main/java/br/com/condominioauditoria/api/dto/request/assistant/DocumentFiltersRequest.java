package br.com.condominioauditoria.api.dto.request.assistant;

import br.com.condominioauditoria.api.model.enums.FileCategory;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Optional filters (RF-04.10), applied by the rag before searching. */
public record DocumentFiltersRequest(
        @JsonProperty("categorias") List<FileCategory> categories,
        @JsonProperty("dataInicio") LocalDate startDate,
        @JsonProperty("dataFim") LocalDate endDate,
        @JsonProperty("arquivoIds") List<UUID> fileIds) {
}
