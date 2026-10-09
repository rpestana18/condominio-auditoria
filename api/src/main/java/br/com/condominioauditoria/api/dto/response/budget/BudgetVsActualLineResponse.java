package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetLineMark;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Budget line with planned, actual, difference and execution, and the cash flow accounts mapped to it. */
public record BudgetVsActualLineResponse(
        @JsonProperty("linhaId") UUID lineId,
        @JsonProperty("codigo") String code,
        @JsonProperty("descricao") String description,
        @JsonProperty("conta") String account,
        @JsonProperty("marca") BudgetLineMark mark,
        @JsonProperty("observacoes") String notes,
        @JsonProperty("pagina") int page,
        @JsonProperty("previsto") BigDecimal planned,
        @JsonProperty("realizado") BigDecimal actual,
        @JsonProperty("diferenca") BigDecimal difference,
        @JsonProperty("execucao") BigDecimal execution,
        @JsonProperty("contasFluxo") List<String> cashFlowAccounts,
        @JsonProperty("lancamentos") int entries) {
}
