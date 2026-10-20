package br.com.condominioauditoria.api.dto.response.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entry that makes up a monthly amount (RF-12.5), with source file and page.
 *
 * @param referenceMonth month of consumption written in the memo ("REF. 08/2026" → 2026-08); null when absent
 */
public record MonthlyEntryResponse(
        UUID entryId,
        LocalDate date,
        String fund,
        String account,
        String accountName,
        String memo,
        BigDecimal amount,
        String referenceMonth,
        UUID fileId,
        String fileName,
        int page) {
}
