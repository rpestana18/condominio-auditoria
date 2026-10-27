package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.OriginMethod;
import br.com.condominioauditoria.api.model.enums.OriginSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Where the amount of a budget line came from (RF-13.13; ADR 0009, Decision 3 B). Only explains: the planned amount is
 * always the budget's ({@code budgeted} of the line, O4). Months as YYYY-MM; {@code indexRate} as a decimal fraction
 * (0.0723 = 7,23%). {@code source} SHEET carries the file, sheet, row and hash of the support sheet.
 */
public record BudgetLineOriginResponse(
        List<String> sourceAccounts,
        String averageFrom,
        String averageTo,
        BigDecimal averageAmount,
        BigDecimal baseAmount,
        OriginMethod method,
        String methodLabel,
        String indexName,
        BigDecimal indexRate,
        boolean indexForecast,
        String indexNote,
        BigDecimal finalAmount,
        String reason,
        OriginSource source,
        UUID fileId,
        String fileName,
        String sheet,
        Integer sheetRow,
        String sha256,
        String changedBy,
        Instant changedAt) {
}
