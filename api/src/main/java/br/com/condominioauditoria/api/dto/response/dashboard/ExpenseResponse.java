package br.com.condominioauditoria.api.dto.response.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseResponse(
        @JsonProperty("data") LocalDate date,
        @JsonProperty("fundo") String fund,
        @JsonProperty("conta") String account,
        @JsonProperty("historico") String memo,
        @JsonProperty("valor") BigDecimal amount,
        @JsonProperty("pagina") int page) {
}
