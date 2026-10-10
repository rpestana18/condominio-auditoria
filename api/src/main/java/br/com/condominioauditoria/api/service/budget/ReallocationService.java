package br.com.condominioauditoria.api.service.budget;

import br.com.condominioauditoria.api.dto.request.budget.ReallocationRequest;
import br.com.condominioauditoria.api.dto.response.budget.ReallocationResponse;
import br.com.condominioauditoria.api.event.BudgetChanged;
import br.com.condominioauditoria.api.mapper.ReallocationMapper;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.accounting.LedgerEntryFingerprint;
import br.com.condominioauditoria.api.model.budget.AccountMapping;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.Reallocation;
import br.com.condominioauditoria.api.model.budget.ReallocationEvent;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.model.enums.AccountMappingStatus;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.enums.MappingTargetType;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.accounting.LedgerEntryRepository;
import br.com.condominioauditoria.api.repository.budget.AccountMappingRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetLineRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.repository.budget.ReallocationEventRepository;
import br.com.condominioauditoria.api.repository.budget.ReallocationRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.service.calculator.BudgetStructure;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator;
import br.com.condominioauditoria.api.util.MoneyFormatter;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Minimal reallocation (RF-03.1.7; ADR 0004, Decision 3): the Gestor or the Admin moves an "a realocar" entry of the
 * Condomínio fund to a budget expense line valid in the month, and can undo it. No AI and no new budget item
 * (RF-02B.3 and RF-02B.5 are out). The original entry does not change; each action stores an event in the trail and
 * triggers the findings recalculation after the commit.
 */
@Service
public class ReallocationService {

    private static final Logger log = LoggerFactory.getLogger(ReallocationService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CondominiumRepository condominiums;
    private final LedgerEntryRepository ledgerEntries;
    private final SourceFileRepository files;
    private final BudgetQueryService budgetQuery;
    private final BudgetRepository budgets;
    private final BudgetLineRepository lines;
    private final AccountMappingRepository mappings;
    private final ReallocationRepository reallocations;
    private final ReallocationEventRepository events;
    private final ApplicationEventPublisher publisher;

    public ReallocationService(CondominiumRepository condominiums, LedgerEntryRepository entries,
            SourceFileRepository files,
            BudgetQueryService budgetQuery, BudgetRepository budgets, BudgetLineRepository lines,
            AccountMappingRepository mappings, ReallocationRepository reallocations,
            ReallocationEventRepository events, ApplicationEventPublisher publisher) {
        this.condominiums = condominiums;
        this.ledgerEntries = entries;
        this.files = files;
        this.budgetQuery = budgetQuery;
        this.budgets = budgets;
        this.lines = lines;
        this.mappings = mappings;
        this.reallocations = reallocations;
        this.events = events;
        this.publisher = publisher;
    }

    @Transactional
    public ReallocationResponse reallocate(UUID condominiumId, ReallocationRequest request, String username) {
        if (request == null || request.entryId() == null || request.lineId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Informe o lançamento e a linha da PO");
        }
        Condominium condominium = condominiums.findById(condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        LedgerEntry l = ledgerEntries.findById(request.entryId())
                .filter(x -> x.getCondominiumId().equals(condominiumId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lançamento não encontrado (se o fluxo foi reprocessado, abra o mês de novo)"));
        SourceFile file = files.findById(l.getFileId())
                .filter(a -> a.getCategory() == FileCategory.TRIAL_BALANCE
                        && BudgetVsActualQueryService.READ_CASH_FLOW.contains(a.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "O lançamento não é de um fluxo de caixa lido"));
        if (!l.getFundId().equals(condominium.getOperatingFundId()) || l.getDebit().signum() == 0
                || l.isInterFundTransfer()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Só débitos do fundo Condomínio (fundo ordinário) são realocados");
        }
        YearMonth month = YearMonth.from(l.getDate());
        Budget budget = budgetQuery.activeInMonth(condominiumId, month)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Sem PO aprovada para "
                        + BudgetVsActualCalculator.mmyyyy(month)));
        AccountMapping mapping = l.getAccountCode() == null ? null
                : mappings.findByBudgetIdAndAccountCode(budget.getId(), l.getAccountCode()).orElse(null);
        if (mapping == null || mapping.getStatus() != AccountMappingStatus.CONFIRMED
                || mapping.getTargetType() != MappingTargetType.TO_REALLOCATE) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Só lançamentos \"a realocar\" são"
                    + " realocados: a conta " + l.getAccountCode() + " não tem de-para confirmado para REALLOCATE");
        }
        List<BudgetLine> readChecks = lines.findByBudgetIdOrderByPosition(budget.getId());
        BudgetLine target = AccountMappingService.debitTargets(BudgetStructure.of(readChecks)).stream()
                .filter(x -> x.getId().equals(request.lineId())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "A linha informada não é uma linha de despesa (1.1 a 1.8) da PO que vale em "
                                + BudgetVsActualCalculator.mmyyyy(month)));
        reallocations.findByBudgetIdAndEntryKeyAndUndoneAtIsNull(budget.getId(), LedgerEntryFingerprint.key(l))
                .ifPresent(r -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Lançamento já realocado em " + r.getReallocatedAt() + ": desfaça antes de realocar de novo");
                });
        Instant now = Instant.now();
        Reallocation r = reallocations.save(new Reallocation(budget, l, file.getSha256(), target, username,
                now));
        String description = "realocação do lançamento de " + DATE.format(l.getDate()) + " (conta " + l.getAccountCode()
                + ", R$ " + MoneyFormatter.format(l.getDebit()) + ") para " + target.getEffectiveCode() + " "
                + target.getDescription();
        events.save(new ReallocationEvent(r, ReallocationEvent.REALLOCATED, username, now, description + "; arquivo "
                + file.getOriginalName() + ", página " + l.getPage() + ", ordem " + l.getSequence()));
        publisher.publishEvent(BudgetChanged.of(condominiumId, description, username, now));
        log.info("Realocação {}: lançamento {} → linha {} por {}", r.getId(), l.getId(), target.getEffectiveCode(),
                username);
        return ReallocationMapper.toResponse(r, target);
    }

    /** Undoing returns the amount to "a realocar"; the reallocation is ended, with the event, and never deleted. */
    @Transactional
    public ReallocationResponse undo(UUID condominiumId, UUID reallocationId, String username) {
        Reallocation r = reallocations.findByIdAndCondominiumId(reallocationId, condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Realocação não encontrada"));
        if (!r.active()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Realocação já desfeita em " + r.getUndoneAt());
        }
        Instant now = Instant.now();
        r.undo(username, now);
        reallocations.save(r);
        BudgetLine line = lines.findByBudgetIdOrderByPosition(r.getBudgetId()).stream()
                .filter(x -> x.getId().equals(r.getBudgetLineId())).findFirst().orElse(null);
        String description = "realocação do lançamento de " + DATE.format(r.getDate()) + " (conta " + r.getAccountCode()
                + ", R$ " + MoneyFormatter.format(r.getAmount()) + ") desfeita";
        events.save(new ReallocationEvent(r, ReallocationEvent.UNDONE, username, now, description
                + (line == null ? "" : "; estava em " + line.getEffectiveCode() + " " + line.getDescription())));
        publisher.publishEvent(BudgetChanged.of(condominiumId, description, username, now));
        log.info("Realocação {} desfeita por {}", r.getId(), username);
        return ReallocationMapper.toResponse(r, line);
    }

    /** Reallocations of the budget version, active and undone (the trail shows both). */
    @Transactional(readOnly = true)
    public List<ReallocationResponse> list(UUID condominiumId, UUID budgetId) {
        Budget budget = budgets.findByIdAndCondominiumId(budgetId, condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO não encontrada"));
        Map<UUID, BudgetLine> byId = lines.findByBudgetIdOrderByPosition(budget.getId()).stream()
                .collect(Collectors.toMap(BudgetLine::getId, Function.identity()));
        return reallocations.findByBudgetIdOrderByDateAscReallocatedAtAsc(budget.getId()).stream()
                .map(r -> ReallocationMapper.toResponse(r, byId.get(r.getBudgetLineId()))).toList();
    }
}
