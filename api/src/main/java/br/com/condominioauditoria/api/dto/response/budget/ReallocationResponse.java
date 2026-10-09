package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Reallocation with the fingerprint of the original entry (which stays untouched in the cash flow). */
public record ReallocationResponse(
        UUID id,
        @JsonProperty("previsaoId") UUID budgetId,
        @JsonProperty("data") LocalDate date,
        @JsonProperty("conta") String account,
        @JsonProperty("contaNome") String accountName,
        @JsonProperty("documento") String document,
        @JsonProperty("historico") String memo,
        @JsonProperty("valor") BigDecimal amount,
        @JsonProperty("arquivoId") UUID fileId,
        String sha256,
        @JsonProperty("pagina") int page,
        @JsonProperty("ordem") int position,
        @JsonProperty("linhaId") UUID lineId,
        @JsonProperty("linhaCodigo") String lineCode,
        @JsonProperty("linhaDescricao") String lineDescription,
        @JsonProperty("realocadaPor") String reallocatedBy,
        @JsonProperty("realocadaEm") Instant reallocatedAt,
        @JsonProperty("desfeitaPor") String undoneBy,
        @JsonProperty("desfeitaEm") Instant undoneAt,
        @JsonProperty("ativa") boolean active) {
}
