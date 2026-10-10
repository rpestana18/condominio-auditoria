package br.com.condominioauditoria.api.dto.response.audit;

import java.util.UUID;

public record FindingEvidenceResponse(
        int position,
        UUID fileId,
        String sha256,
        Integer page,
        String reference,
        UUID budgetLineId) {
}
