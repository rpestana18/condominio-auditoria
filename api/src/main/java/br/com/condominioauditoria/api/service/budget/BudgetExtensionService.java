package br.com.condominioauditoria.api.service.budget;

import br.com.condominioauditoria.api.dto.request.budget.BudgetExtensionRequest;
import br.com.condominioauditoria.api.dto.response.budget.BudgetDetailResponse;
import br.com.condominioauditoria.api.event.BudgetChanged;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetEvent;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.orcamento.CalculoPrevistoRealizado;
import br.com.condominioauditoria.api.repository.budget.BudgetEventRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.service.calculator.BudgetValidity;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Extended budget (RF-11.3; ADR 0005, Decision 4): the Admin marks the confirmed budget as extended until a month after
 * the end of the fiscal year, with a justification. Never automatic. Rejected if any extended month already has a
 * confirmed budget or another budget's extension. The extended months use this budget and its account mapping; the
 * findings are recalculated afterwards.
 */
@Service
public class BudgetExtensionService {

    private static final Logger log = LoggerFactory.getLogger(BudgetExtensionService.class);

    private final CondominiumRepository condominiums;
    private final BudgetRepository budgets;
    private final BudgetEventRepository events;
    private final BudgetQueryService query;
    private final ApplicationEventPublisher publisher;

    public BudgetExtensionService(CondominiumRepository condominiums, BudgetRepository budgets,
            BudgetEventRepository events, BudgetQueryService query, ApplicationEventPublisher publisher) {
        this.condominiums = condominiums;
        this.budgets = budgets;
        this.events = events;
        this.query = query;
        this.publisher = publisher;
    }

    @Transactional
    public BudgetDetailResponse extend(UUID condominiumId, UUID budgetId, BudgetExtensionRequest request,
            String username) {
        Budget budget = budgetForWrite(condominiumId, budgetId);
        String justification = request == null || request.justification() == null ? "" : request.justification().trim();
        if (justification.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "A prorrogação exige justificativa");
        }
        YearMonth until = month(request.until());
        YearMonth first = budget.getFiscalYearEnd().plusMonths(1);
        if (until.isBefore(first)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "A prorrogação termina depois do fim"
                    + " do exercício (" + budget.getFiscalYearEnd() + "): informe " + first + " ou depois");
        }
        List<Budget> others = budgets.findByCondominiumIdAndStatusIn(condominiumId,
                        EnumSet.of(BudgetStatus.CONFIRMADA, BudgetStatus.SUBSTITUIDA)).stream()
                .filter(p -> !p.getId().equals(budget.getId())).toList();
        for (YearMonth month : CalculoPrevistoRealizado.meses(first, until)) {
            Optional<BudgetValidity.BudgetOfMonth> ofMonth = BudgetValidity.ofMonth(others, month);
            if (ofMonth.isPresent()) {
                Budget other = ofMonth.get().budget();
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        CalculoPrevistoRealizado.mmaaaa(month)
                        + (ofMonth.get().extended() ? " já está na prorrogação de outra PO" : " já tem PO confirmada")
                        + " (versão " + other.getVersion() + ", exercício " + other.getFiscalYearStart() + " a "
                        + other.getFiscalYearEnd() + ")");
            }
        }
        Instant now = Instant.now();
        String before = budget.getExtendedUntil() == null ? "sem prorrogação" : "prorrogada até " + budget.getExtendedUntil();
        budget.extend(until, justification, username, now);
        budgets.save(budget);
        events.save(new BudgetEvent(budget, BudgetEvent.EXTENDED, username, now, justification,
                "Prorrogada de " + first + " a " + until + " (antes: " + before + ")."));
        publisher.publishEvent(BudgetChanged.of(condominiumId, "PO versão " + budget.getVersion() + " prorrogada até "
                + until, username, now));
        log.info("PO {} prorrogada até {} por {}", budget.getId(), until, username);
        return query.detail(budget);
    }

    @Transactional
    public BudgetDetailResponse undo(UUID condominiumId, UUID budgetId, String username) {
        Budget budget = budgetForWrite(condominiumId, budgetId);
        YearMonth until = budget.getExtendedUntil();
        if (until == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A PO não está prorrogada");
        }
        Instant now = Instant.now();
        budget.undoExtension();
        budgets.save(budget);
        events.save(new BudgetEvent(budget, BudgetEvent.EXTENSION_UNDONE, username, now, null,
                "Prorrogação de " + budget.getFiscalYearEnd().plusMonths(1) + " a " + until + " desfeita."));
        publisher.publishEvent(BudgetChanged.of(condominiumId, "prorrogação da PO versão " + budget.getVersion()
                + " desfeita", username, now));
        log.info("Prorrogação da PO {} desfeita por {}", budget.getId(), username);
        return query.detail(budget);
    }

    private Budget budgetForWrite(UUID condominiumId, UUID budgetId) {
        // One budget change at a time in the condominium, as in confirmation
        condominiums.lockById(condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        Budget budget = budgets.findByIdAndCondominiumId(budgetId, condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO não encontrada"));
        if (budget.getStatus() != BudgetStatus.CONFIRMADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, budget.getStatus() == BudgetStatus.SUBSTITUIDA
                    ? "PO substituída por outra versão: prorrogue a versão que vale"
                    : "Confirme a PO antes de prorrogar");
        }
        return budget;
    }

    private static YearMonth month(String text) {
        try {
            return YearMonth.parse(text == null ? "" : text.trim());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Informe o último mês prorrogado no formato AAAA-MM: " + text);
        }
    }
}
