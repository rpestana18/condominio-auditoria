package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/** Budget detail for the PO screen: lines, checks, warnings, repeated codes, fund links and findings. */
public record BudgetDetailResponse(
        @JsonProperty("previsao") BudgetSummaryResponse budget,
        @JsonProperty("colunaOrcadoAnterior") String previousBudgetedColumn,
        @JsonProperty("colunaOrcado") String budgetedColumn,
        @JsonProperty("previstoMesImpresso") BigDecimal printedMonthlyPlanned,
        @JsonProperty("toleranciaArredondamento") BigDecimal roundingTolerance,
        @JsonProperty("substituidaDesde") String supersededFrom,
        @JsonProperty("confirmacao") BudgetConfirmationResponse confirmation,
        @JsonProperty("linhas") List<BudgetLineResponse> lines,
        @JsonProperty("conferencias") List<BudgetCheckResponse> checks,
        @JsonProperty("avisos") List<BudgetWarningResponse> warnings,
        @JsonProperty("codigosRepetidos") List<RepeatedCodeResponse> repeatedCodes,
        @JsonProperty("fundos") List<BudgetFundLineResponse> funds,
        @JsonProperty("achados") List<BudgetFindingResponse> findings) {
}
