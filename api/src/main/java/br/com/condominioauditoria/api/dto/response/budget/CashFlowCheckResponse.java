package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Fund debits total = actual expense + adjustments + transfers (RF-03.1.6). */
public record CashFlowCheckResponse(
        @JsonProperty("debitosDoFundo") BigDecimal fundDebits,
        @JsonProperty("lancamentos") int entries,
        @JsonProperty("despesaRealizada") BigDecimal actualExpense,
        @JsonProperty("ajustes") BigDecimal adjustments,
        @JsonProperty("transferencias") BigDecimal transfers,
        @JsonProperty("confere") boolean matches) {
}
