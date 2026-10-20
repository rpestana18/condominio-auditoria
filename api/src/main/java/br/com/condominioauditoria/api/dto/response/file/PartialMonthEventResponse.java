package br.com.condominioauditoria.api.dto.response.file;

import br.com.condominioauditoria.api.model.enums.PartialMonthAction;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Event of the partial month trail. */
public record PartialMonthEventResponse(
        UUID id,
        UUID fileId,
        PartialMonthAction action,
        boolean previousPartial,
        boolean newPartial,
        LocalDate partialUntil,
        UUID supersededById,
        String username,
        Instant occurredAt,
        String reason) {
}
