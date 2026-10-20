package br.com.condominioauditoria.api.dto.response.file;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Partial month state of a read cash flow (RF-13.10). {@code automatic} true = no manual mark (the rule of the upload date holds). {@code supersededById}: complete cash flow of the same month that replaced this partial one. */
public record PartialMonthResponse(
        UUID fileId,
        String name,
        LocalDate periodStart,
        LocalDate periodEnd,
        Instant uploadedAt,
        String uploadedBy,
        boolean partial,
        boolean automatic,
        boolean automaticallyPartial,
        LocalDate partialUntil,
        String reason,
        String changedBy,
        Instant changedAt,
        UUID supersededById) {
}
