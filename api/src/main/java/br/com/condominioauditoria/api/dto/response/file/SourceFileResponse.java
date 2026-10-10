package br.com.condominioauditoria.api.dto.response.file;

import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.enums.FileStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SourceFileResponse(
        UUID id,
        FileCategory category,
        String categoryLabel,
        String name,
        long sizeBytes,
        FileStatus status,
        String message,
        LocalDate periodStart,
        LocalDate periodEnd,
        Integer entryCount,
        String uploadedBy,
        Instant uploadedAt,
        Instant processedAt,
        IndexingResponse indexing) {
}
