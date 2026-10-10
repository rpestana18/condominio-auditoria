package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.FiscalYearType;
import java.util.List;
import java.util.UUID;

/** Compared fiscal year; {@code period} is the evidence period in budget vs. actual ("cumulative" or YYYY-MM). */
public record ComparedFiscalYearResponse(
        String id,
        FiscalYearType type,
        String label,
        UUID budgetId,
        Integer version,
        String start,
        String end,
        String period,
        List<String> months) {
}
