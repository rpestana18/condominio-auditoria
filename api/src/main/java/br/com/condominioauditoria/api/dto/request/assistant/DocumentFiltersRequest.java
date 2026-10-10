package br.com.condominioauditoria.api.dto.request.assistant;

import br.com.condominioauditoria.api.model.enums.FileCategory;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Optional filters (RF-04.10), applied by the rag before searching. */
public record DocumentFiltersRequest(
        List<FileCategory> categories,
        LocalDate startDate,
        LocalDate endDate,
        List<UUID> fileIds) {
}
