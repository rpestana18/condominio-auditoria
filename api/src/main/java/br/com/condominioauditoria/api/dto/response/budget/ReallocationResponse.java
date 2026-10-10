package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Reallocation with the fingerprint of the original entry (which stays untouched in the cash flow). */
public record ReallocationResponse(
        UUID id,
        UUID budgetId,
        LocalDate date,
        String account,
        String accountName,
        String document,
        String memo,
        BigDecimal amount,
        UUID fileId,
        String sha256,
        int page,
        int position,
        UUID lineId,
        String lineCode,
        String lineDescription,
        String reallocatedBy,
        Instant reallocatedAt,
        String undoneBy,
        Instant undoneAt,
        boolean active) {
}
