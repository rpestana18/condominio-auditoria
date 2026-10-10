package br.com.condominioauditoria.api.dto.response.file;

import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.enums.FileStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SourceFileResponse(
        UUID id,
        @JsonProperty("categoria") FileCategory category,
        @JsonProperty("categoriaRotulo") String categoryLabel,
        @JsonProperty("nome") String name,
        @JsonProperty("tamanhoBytes") long sizeBytes,
        FileStatus status,
        @JsonProperty("mensagem") String message,
        @JsonProperty("periodoInicio") LocalDate periodStart,
        @JsonProperty("periodoFim") LocalDate periodEnd,
        @JsonProperty("totalLancamentos") Integer entryCount,
        @JsonProperty("enviadoPor") String uploadedBy,
        @JsonProperty("enviadoEm") Instant uploadedAt,
        @JsonProperty("processadoEm") Instant processedAt,
        @JsonProperty("indexing") IndexingResponse indexing) {
}
