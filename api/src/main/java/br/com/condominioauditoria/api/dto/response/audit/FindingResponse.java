package br.com.condominioauditoria.api.dto.response.audit;

import br.com.condominioauditoria.api.model.enums.FindingStatus;
import br.com.condominioauditoria.api.model.enums.Severity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FindingResponse(
        UUID id,
        String rule,
        String ruleVersion,
        Severity severity,
        String referenceMonth,
        String target,
        String description,
        FindingStatus status,
        String statusReason,
        Instant statusChangedAt,
        boolean conditionPresent,
        Instant createdAt,
        List<FindingEvidenceResponse> evidence,
        List<FindingEventResponse> history) {
}
