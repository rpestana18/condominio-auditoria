package br.com.condominioauditoria.api.dto.response.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DashboardResponse(
        @JsonProperty("arquivoId") UUID fileId,
        @JsonProperty("arquivoNome") String fileName,
        @JsonProperty("periodoInicio") LocalDate periodStart,
        @JsonProperty("periodoFim") LocalDate periodEnd,
        @JsonProperty("saldoAnterior") BigDecimal openingBalance,
        @JsonProperty("entradas") BigDecimal inflows,
        @JsonProperty("saidas") BigDecimal outflows,
        @JsonProperty("saldoAtual") BigDecimal closingBalance,
        @JsonProperty("conferenciasComFalha") long failedChecks,
        @JsonProperty("fundoOrdinario") OperatingFundResponse operatingFund,
        @JsonProperty("fundos") List<FundPeriodResponse> funds,
        @JsonProperty("maioresDespesas") List<ExpenseResponse> largestExpenses) {
}
