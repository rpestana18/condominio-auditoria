package br.com.condominioauditoria.rag.model.cashflow;

import java.time.LocalDate;
import java.util.List;

/** Monthly cash flow of the management company, already parsed, still without any audit judgment. */
public record CashFlow(
        String property,
        LocalDate periodStart,
        LocalDate periodEnd,
        List<FundSection> sections,
        List<FundPosition> financialPosition,
        FundPosition positionTotal) {

    public int entryCount() {
        return sections.stream().mapToInt(s -> s.entries().size()).sum();
    }
}
