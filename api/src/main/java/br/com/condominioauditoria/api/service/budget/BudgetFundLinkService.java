package br.com.condominioauditoria.api.service.budget;

import br.com.condominioauditoria.api.dto.request.budget.BudgetFundsRequest;
import br.com.condominioauditoria.api.dto.request.budget.FundLinkRequest;
import br.com.condominioauditoria.api.dto.response.budget.BudgetDetailResponse;
import br.com.condominioauditoria.api.event.BudgetChanged;
import br.com.condominioauditoria.api.exception.BudgetConfirmationRejectedException;
import br.com.condominioauditoria.api.model.accounting.Fund;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetEvent;
import br.com.condominioauditoria.api.model.budget.BudgetFundLink;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.repository.accounting.FundRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetEventRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetFundLinkRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetLineRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.service.calculator.BudgetStructure;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
 * Change of the link of the 1.9.x lines to the cash flow funds after the budget is confirmed (RF-03.1.9 and
 * RF-03.1.13): Admin only; each line with at most one fund and each fund on at most one line; never the operating fund.
 * The list sent is the complete link: a missing line or one with a null fund ends up without a fund ("linha 1.9.x sem
 * fundo ligado"). The trail (event FUNDS_CHANGED) records each line's previous and new fund, with who and when, and
 * the findings are recalculated after the commit.
 */
@Service
public class BudgetFundLinkService {

    private static final Logger log = LoggerFactory.getLogger(BudgetFundLinkService.class);
    static final String NO_FUND = "(sem fundo)";

    private final CondominiumRepository condominiums;
    private final BudgetRepository budgets;
    private final BudgetLineRepository lines;
    private final BudgetFundLinkRepository fundLinks;
    private final FundRepository funds;
    private final BudgetEventRepository events;
    private final BudgetQueryService query;
    private final ApplicationEventPublisher publisher;

    public BudgetFundLinkService(CondominiumRepository condominiums, BudgetRepository budgets,
            BudgetLineRepository lines, BudgetFundLinkRepository fundLinks, FundRepository funds,
            BudgetEventRepository events, BudgetQueryService query, ApplicationEventPublisher publisher) {
        this.condominiums = condominiums;
        this.budgets = budgets;
        this.lines = lines;
        this.fundLinks = fundLinks;
        this.funds = funds;
        this.events = events;
        this.query = query;
        this.publisher = publisher;
    }

    @Transactional
    public BudgetDetailResponse change(UUID condominiumId, UUID budgetId, BudgetFundsRequest request, String username) {
        Condominium condominium = condominiums.lockById(condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        Budget budget = budgets.findByIdAndCondominiumId(budgetId, condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO não encontrada"));
        if (!budget.getStatus().isLocked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A PO ainda não foi confirmada: ligue os fundos na confirmação");
        }
        List<FundLinkRequest> requests = request == null || request.funds() == null ? List.of() : request.funds();
        List<BudgetLine> fundLines = BudgetStructure.of(lines.findByBudgetIdOrderByPosition(budget.getId())).funds()
                .map(BudgetStructure.Group::lines).orElse(List.of());
        Map<UUID, BudgetLine> byId = fundLines.stream().collect(Collectors.toMap(BudgetLine::getId,
                Function.identity()));
        Map<UUID, Fund> ofCondominium = funds.findByCondominiumId(condominiumId).stream()
                .collect(Collectors.toMap(Fund::getId, Function.identity()));

        List<String> reasons = new ArrayList<>();
        Map<UUID, UUID> newLinks = new LinkedHashMap<>();
        Set<UUID> seenLines = new HashSet<>();
        Set<UUID> usedFunds = new HashSet<>();
        for (FundLinkRequest f : requests) {
            BudgetLine line = f.lineId() == null ? null : byId.get(f.lineId());
            if (line == null) {
                reasons.add("Ligação de fundo para uma linha que não é linha de fundo desta PO.");
                continue;
            }
            if (!seenLines.add(line.getId())) {
                reasons.add("Mais de um fundo para a linha " + line.getEffectiveCode() + ".");
                continue;
            }
            if (f.fundId() == null) {
                continue;
            }
            Fund fund = ofCondominium.get(f.fundId());
            if (fund == null) {
                reasons.add("O fundo informado para a linha " + line.getEffectiveCode() + " não é deste condomínio.");
            } else if (fund.getId().equals(condominium.getOperatingFundId())) {
                reasons.add("O fundo \"" + fund.getName() + "\" é o fundo ordinário e não pode ser ligado à linha "
                        + line.getEffectiveCode() + ".");
            } else if (!usedFunds.add(fund.getId())) {
                reasons.add("O fundo \"" + fund.getName() + "\" foi ligado a mais de uma linha de fundo.");
            } else {
                newLinks.put(line.getId(), fund.getId());
            }
        }
        if (!reasons.isEmpty()) {
            throw new BudgetConfirmationRejectedException(HttpStatus.UNPROCESSABLE_CONTENT, reasons);
        }

        Map<UUID, UUID> previous = new HashMap<>();
        fundLinks.findByBudgetId(budget.getId()).forEach(p -> previous.put(p.getBudgetLineId(), p.getFundId()));
        List<String> changes = new ArrayList<>();
        for (BudgetLine l : fundLines) {
            UUID before = previous.get(l.getId());
            UUID after = newLinks.get(l.getId());
            if (!Objects.equals(before, after)) {
                changes.add(l.getEffectiveCode() + " " + l.getDescription() + ": " + name(ofCondominium, before) + " → "
                        + name(ofCondominium, after));
            }
        }
        if (changes.isEmpty()) {
            return query.detail(budget);
        }
        Instant now = Instant.now();
        fundLinks.deleteByBudgetId(budget.getId());
        newLinks.forEach((line, fund) -> fundLinks.save(new BudgetFundLink(budget.getId(), line, fund)));
        events.save(new BudgetEvent(budget, BudgetEvent.FUNDS_CHANGED, username, now, null,
                "Ligação dos fundos alterada: " + String.join("; ", changes)));
        publisher.publishEvent(BudgetChanged.of(condominiumId, "ligação dos fundos da PO versão " + budget.getVersion()
                + " alterada (" + String.join("; ", changes) + ")", username, now));
        log.info("PO {}: ligação dos fundos alterada por {}: {}", budget.getId(), username, changes);
        return query.detail(budget);
    }

    private static String name(Map<UUID, Fund> funds, UUID id) {
        if (id == null) {
            return NO_FUND;
        }
        Fund f = funds.get(id);
        return f == null ? "fundo " + id : f.getName();
    }
}
