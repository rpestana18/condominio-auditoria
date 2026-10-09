package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Budget vs. actual result (RF-03.1.6 to RF-03.1.11), the same object for the screen, the export and the golden test.
 * Immutable and with nothing calculated on read: everything comes from {@link BudgetVsActualCalculator}. Money in
 * BigDecimal with 2 decimals; percentages with 1 decimal (half up), null when the planned amount is zero ("—").
 *
 * <p>With a {@code status} other than CALCULADO, the numbers are null and {@code message} says why: the system never
 * shows zero in place of a number that was not assessed (RF-03.1.10).
 */
public record BudgetVsActualResponse(
        @JsonProperty("versaoCalculo") String calculationVersion,
        @JsonProperty("periodo") String period,
        @JsonProperty("situacao") BudgetVsActualStatus status,
        @JsonProperty("mensagem") String message,
        @JsonProperty("po") BudgetBriefResponse budget,
        @JsonProperty("meses") List<FiscalYearMonthResponse> months,
        @JsonProperty("mesesSomados") List<String> summedMonths,
        @JsonProperty("mesesSemFluxo") List<String> monthsWithoutCashFlow,
        @JsonProperty("mesesComDoisFluxos") List<String> monthsWithTwoCashFlows,
        @JsonProperty("depara") PeriodMappingSummaryResponse mapping,
        @JsonProperty("provisorio") boolean provisional,
        @JsonProperty("totais") BudgetVsActualTotalsResponse totals,
        @JsonProperty("grupos") List<BudgetVsActualGroupResponse> groups,
        @JsonProperty("ajustes") EntryBlockResponse adjustments,
        @JsonProperty("aRealocar") EntryBlockResponse toReallocate,
        @JsonProperty("semLinhaPo") EntryBlockResponse withoutBudgetLine,
        @JsonProperty("conferencia") CashFlowCheckResponse cashFlowCheck,
        @JsonProperty("regra20") Rule20Response rule20,
        @JsonProperty("fundos") List<FundResultResponse> funds,
        @JsonProperty("avisos") List<BudgetVsActualWarningResponse> warnings) {
}
