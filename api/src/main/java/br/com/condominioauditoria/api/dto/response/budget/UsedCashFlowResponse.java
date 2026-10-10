package br.com.condominioauditoria.api.dto.response.budget;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Cash flow file used by the calculation, with period, hash and upload. */
public record UsedCashFlowResponse(
        UUID fileId,
        String name,
        String sha256,
        LocalDate periodStart,
        LocalDate periodEnd,
        Instant uploadedAt,
        String uploadedBy) {
}
