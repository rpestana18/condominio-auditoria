package br.com.condominioauditoria.api.mapper;

import br.com.condominioauditoria.api.dto.response.audit.FindingEventResponse;
import br.com.condominioauditoria.api.dto.response.audit.FindingEvidenceResponse;
import br.com.condominioauditoria.api.dto.response.audit.FindingResponse;
import br.com.condominioauditoria.api.model.audit.Finding;
import br.com.condominioauditoria.api.model.audit.FindingEvent;
import br.com.condominioauditoria.api.model.audit.FindingEvidence;
import java.util.List;

/** Findings, their evidence and their history as shown on the screen. */
public final class FindingMapper {

    private FindingMapper() {
    }

    public static FindingResponse toResponse(Finding f, List<FindingEvidenceResponse> evidence,
            List<FindingEventResponse> history) {
        return new FindingResponse(f.getId(), f.getRule(), f.getRuleVersion(), f.getSeverity(),
                f.getReferenceMonth().toString(), f.getTarget(), f.getDescription(), f.getStatus(), f.getStatusReason(),
                f.getStatusChangedAt(), f.isConditionPresent(), f.getCreatedAt(), evidence, history);
    }

    public static FindingEvidenceResponse toResponse(FindingEvidence e) {
        return new FindingEvidenceResponse(e.getPosition(), e.getFileId(), e.getSha256(), e.getPage(), e.getReference(),
                e.getBudgetLineId());
    }

    public static FindingEventResponse toResponse(FindingEvent e) {
        return new FindingEventResponse(e.getPreviousStatus(), e.getNewStatus(), e.isConditionPresent(), e.getReason(),
                e.getUsername(), e.getOccurredAt());
    }
}
