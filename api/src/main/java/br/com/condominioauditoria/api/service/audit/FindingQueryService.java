package br.com.condominioauditoria.api.service.audit;

import br.com.condominioauditoria.api.dto.response.audit.FindingEventResponse;
import br.com.condominioauditoria.api.dto.response.audit.FindingEvidenceResponse;
import br.com.condominioauditoria.api.dto.response.audit.FindingResponse;
import br.com.condominioauditoria.api.mapper.FindingMapper;
import br.com.condominioauditoria.api.model.audit.Finding;
import br.com.condominioauditoria.api.model.audit.FindingEvent;
import br.com.condominioauditoria.api.model.audit.FindingEvidence;
import br.com.condominioauditoria.api.repository.audit.FindingEventRepository;
import br.com.condominioauditoria.api.repository.audit.FindingEvidenceRepository;
import br.com.condominioauditoria.api.repository.audit.FindingRepository;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Findings with the original evidence and the history (RF-03.1.12): a finding never disappears, it only changes status.
 */
@Service
public class FindingQueryService {

    private final FindingRepository findings;
    private final FindingEvidenceRepository evidenceRecords;
    private final FindingEventRepository events;

    public FindingQueryService(FindingRepository findings, FindingEvidenceRepository evidenceRecords,
            FindingEventRepository events) {
        this.findings = findings;
        this.evidenceRecords = evidenceRecords;
        this.events = events;
    }

    /** {@code referenceMonth}: YYYY-MM, or null for every month. */
    @Transactional(readOnly = true)
    public List<FindingResponse> list(UUID condominiumId, String referenceMonth) {
        List<Finding> list;
        if (referenceMonth == null || referenceMonth.isBlank()) {
            list = findings.findByCondominiumIdOrderByReferenceMonthDescCreatedAtAsc(condominiumId);
        } else {
            YearMonth month;
            try {
                month = YearMonth.parse(referenceMonth.trim());
            } catch (DateTimeParseException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Competência deve ser AAAA-MM: " + referenceMonth);
            }
            list = findings.findByCondominiumIdAndReferenceMonthOrderByCreatedAt(condominiumId, month.atDay(1));
        }
        if (list.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = list.stream().map(Finding::getId).toList();
        Map<UUID, List<FindingEvidenceResponse>> evidence = evidenceRecords.findByFindingIdInOrderByPositionAsc(ids)
                .stream().collect(Collectors.groupingBy(FindingEvidence::getFindingId,
                        Collectors.mapping(FindingMapper::toResponse, Collectors.toList())));
        Map<UUID, List<FindingEventResponse>> history = events.findByFindingIdInOrderByOccurredAtAsc(ids).stream()
                .collect(Collectors.groupingBy(FindingEvent::getFindingId,
                        Collectors.mapping(FindingMapper::toResponse, Collectors.toList())));
        return list.stream().map(f -> FindingMapper.toResponse(f, evidence.getOrDefault(f.getId(), List.of()),
                history.getOrDefault(f.getId(), List.of()))).toList();
    }
}
