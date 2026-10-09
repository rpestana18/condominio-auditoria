package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Entry that makes up a number (RF-03.1.12), with source file, page and hash. */
public record EvidenceResponse(
        @JsonProperty("lancamentoId") UUID entryId,
        @JsonProperty("data") LocalDate date,
        @JsonProperty("conta") String account,
        @JsonProperty("contaNome") String accountName,
        @JsonProperty("historico") String memo,
        @JsonProperty("fornecedor") String supplier,
        @JsonProperty("documento") String document,
        @JsonProperty("valor") BigDecimal amount,
        @JsonProperty("fundo") String fund,
        @JsonProperty("arquivoId") UUID fileId,
        @JsonProperty("arquivoNome") String fileName,
        String sha256,
        @JsonProperty("pagina") int page,
        @JsonProperty("ordem") int position,
        @JsonProperty("realocacao") String reallocation,
        @JsonProperty("realocacaoId") UUID reallocationId) {
}
