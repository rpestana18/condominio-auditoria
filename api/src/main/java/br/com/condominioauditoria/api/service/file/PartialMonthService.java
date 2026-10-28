package br.com.condominioauditoria.api.service.file;

import br.com.condominioauditoria.api.dto.request.file.PartialMonthRequest;
import br.com.condominioauditoria.api.dto.response.file.PartialMonthEventResponse;
import br.com.condominioauditoria.api.dto.response.file.PartialMonthResponse;
import br.com.condominioauditoria.api.event.BudgetChanged;
import br.com.condominioauditoria.api.mapper.PartialMonthMapper;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.enums.PartialMonthAction;
import br.com.condominioauditoria.api.model.file.PartialMonthEvent;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.file.PartialMonthEventRepository;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.service.budget.BudgetVsActualQueryService;
import br.com.condominioauditoria.api.service.calculator.PartialCashFlows;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Partial month of the cash flows (RF-13.10; ADR 0007, Decision 5 A). A cash flow uploaded on or before the last day of
 * its period is partial ("parcial até DD/MM"); the Manager or the Admin can mark it partial or complete, or go back to
 * the automatic rule, always with the trail. The complete cash flow of the same month replaces the partial one, and
 * the trail records the replacement. A change recalculates the findings after the commit (a partial month creates no
 * finding).
 */
@Service
public class PartialMonthService {

    private static final Logger log = LoggerFactory.getLogger(PartialMonthService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SourceFileRepository files;
    private final PartialMonthEventRepository events;
    private final BudgetVsActualQueryService cashFlows;
    private final ApplicationEventPublisher publisher;

    public PartialMonthService(SourceFileRepository files, PartialMonthEventRepository events,
            BudgetVsActualQueryService cashFlows, ApplicationEventPublisher publisher) {
        this.files = files;
        this.events = events;
        this.cashFlows = cashFlows;
        this.publisher = publisher;
    }

    /** Every read cash flow of the condominium with its partial month state, the most recent period first. */
    @Transactional(readOnly = true)
    public List<PartialMonthResponse> list(UUID condominiumId) {
        PartialCashFlows selection = cashFlows.selectCashFlows(condominiumId);
        List<UUID> ids = new java.util.ArrayList<>(selection.effective().stream()
                .map(f -> f.fileId()).toList());
        ids.addAll(selection.superseded().keySet());
        return files.findByCondominiumIdAndIdIn(condominiumId, ids).stream()
                .sorted(Comparator.comparing(SourceFile::getPeriodEnd).reversed()
                        .thenComparing(SourceFile::getUploadedAt, Comparator.reverseOrder()))
                .map(f -> PartialMonthMapper.toResponse(f, selection.superseded().get(f.getId())))
                .toList();
    }

    /**
     * Manual mark (Manager or Admin): {@code partial} true = partial (until the given date, or the upload date), false =
     * complete, null = back to the automatic rule. Only for read cash flows (trial balances category, with a period).
     */
    @Transactional
    public PartialMonthResponse mark(UUID condominiumId, UUID fileId, PartialMonthRequest request, String username) {
        SourceFile f = files.findByIdAndCondominiumId(fileId, condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado"));
        if (f.getCategory() != FileCategory.TRIAL_BALANCE || f.getPeriodStart() == null || f.getPeriodEnd() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Só um extrato lido, da categoria \""
                    + FileCategory.TRIAL_BALANCE.label() + "\" e com período, pode ser marcado como mês parcial");
        }
        Boolean partial = request == null ? null : request.partial();
        LocalDate until = request == null ? null : request.until();
        if (Boolean.TRUE.equals(partial) && until != null
                && (until.isBefore(f.getPeriodStart()) || until.isAfter(f.getPeriodEnd()))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "A data \"parcial até\" ("
                    + DATE.format(until) + ") deve estar no período do extrato (" + DATE.format(f.getPeriodStart())
                    + " a " + DATE.format(f.getPeriodEnd()) + ")");
        }
        String reason = request == null || request.reason() == null || request.reason().isBlank() ? null
                : request.reason().trim();
        if (Objects.equals(f.getPartialOverride(), partial) && Objects.equals(f.getPartialUntil(),
                Boolean.TRUE.equals(partial) ? until : null)) {
            return PartialMonthMapper.toResponse(f, cashFlows.selectCashFlows(condominiumId).superseded()
                    .get(f.getId()));
        }
        boolean before = f.isPartial();
        Instant now = Instant.now();
        f.markPartial(partial, until, reason, username, now);
        files.save(f);
        PartialMonthAction action = partial == null ? PartialMonthAction.AUTOMATIC
                : partial ? PartialMonthAction.MARKED_PARTIAL : PartialMonthAction.MARKED_COMPLETE;
        events.save(new PartialMonthEvent(f, action, before, null, username, now, reason));
        publisher.publishEvent(BudgetChanged.of(condominiumId, "mês parcial do extrato " + f.getOriginalName()
                + ": " + (f.isPartial() ? "parcial até " + DATE.format(f.effectivePartialUntil()) : "completo"),
                username, now));
        log.info("Extrato {} marcado {}", f.getId(), action);
        return PartialMonthMapper.toResponse(f, null);
    }

    @Transactional(readOnly = true)
    public List<PartialMonthEventResponse> events(UUID condominiumId, UUID fileId) {
        SourceFile f = files.findByIdAndCondominiumId(fileId, condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado"));
        return events.findByFileIdOrderByOccurredAt(f.getId()).stream().map(PartialMonthMapper::toResponse).toList();
    }

    /**
     * Records in the trail each partial cash flow that a complete one of the same month now replaces (RF-13.10). Called
     * after a cash flow is saved; idempotent (each replacement is recorded once).
     */
    public void recordReplacements(UUID condominiumId) {
        PartialCashFlows selection = cashFlows.selectCashFlows(condominiumId);
        Instant now = Instant.now();
        selection.superseded().forEach((partialId, completeId) -> {
            if (events.existsByFileIdAndSupersededById(partialId, completeId)) {
                return;
            }
            SourceFile partial = files.findById(partialId).orElse(null);
            SourceFile complete = files.findById(completeId).orElse(null);
            if (partial == null || complete == null) {
                return;
            }
            events.save(new PartialMonthEvent(partial, PartialMonthAction.SUPERSEDED, true, complete, "sistema", now,
                    "Substituído pelo extrato completo " + complete.getOriginalName() + " (" + complete.getSha256()
                            + ")"));
            log.info("Extrato parcial {} substituído pelo completo {}", partialId, completeId);
        });
    }
}
