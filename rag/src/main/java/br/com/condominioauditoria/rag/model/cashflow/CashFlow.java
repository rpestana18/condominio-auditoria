package br.com.condominioauditoria.rag.model.cashflow;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/** Monthly cash flow of the management company, already parsed, still without any audit judgment. */
public record CashFlow(
        @JsonProperty("empreendimento") String property,
        @JsonProperty("periodoInicio") LocalDate periodStart,
        @JsonProperty("periodoFim") LocalDate periodEnd,
        @JsonProperty("secoes") List<FundSection> sections,
        @JsonProperty("posicaoFinanceira") List<FundPosition> financialPosition,
        @JsonProperty("totalPosicao") FundPosition positionTotal) {

    public int entryCount() {
        return sections.stream().mapToInt(s -> s.entries().size()).sum();
    }
}
