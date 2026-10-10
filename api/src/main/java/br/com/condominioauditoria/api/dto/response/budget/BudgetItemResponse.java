package br.com.condominioauditoria.api.dto.response.budget;

import java.time.Instant;
import java.util.UUID;

/** Budget item of the condominium's catalog (contracts/openapi.yaml). */
public record BudgetItemResponse(
        UUID id,
        String name,
        String group,
        UUID sourceLineId,
        String createdBy,
        Instant createdAt) {
}
