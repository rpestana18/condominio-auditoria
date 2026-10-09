package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/** Budget item of the condominium's catalog (contracts/openapi.yaml). */
public record BudgetItemResponse(
        UUID id,
        @JsonProperty("nome") String name,
        @JsonProperty("grupo") String group,
        @JsonProperty("linhaOrigemId") UUID sourceLineId,
        @JsonProperty("criadaPor") String createdBy,
        @JsonProperty("criadaEm") Instant createdAt) {
}
