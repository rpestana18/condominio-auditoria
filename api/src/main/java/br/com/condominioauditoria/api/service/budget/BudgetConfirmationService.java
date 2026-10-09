package br.com.condominioauditoria.api.service.budget;

import static br.com.condominioauditoria.api.orcamento.DinheiroBr.formatar;

import br.com.condominioauditoria.api.dto.request.budget.BudgetConfirmationRequest;
import br.com.condominioauditoria.api.dto.request.budget.EffectiveCodeRequest;
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
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.orcamento.ServicoRubricas;
import br.com.condominioauditoria.api.repository.accounting.FundRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetEventRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetFundLinkRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetLineRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.service.audit.FindingSyncService;
import br.com.condominioauditoria.api.service.audit.FindingSyncService.Evidence;
import br.com.condominioauditoria.api.service.audit.rule.ReserveFundCapRule;
import br.com.condominioauditoria.api.service.calculator.BudgetReadingAssessment;
import br.com.condominioauditoria.api.service.calculator.BudgetStructure;
import br.com.condominioauditoria.api.service.calculator.BudgetValidity;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Budget confirmation by the Admin (RF-03.1.3; RF-03.1.2 and Q29; ADR 0004, Decision 8). Rejects with every reason at
 * once. The read values are never edited: confirmation only records the fiscal year, minutes, effective codes of the
 * repeated lines, the fund links and, when the budget was read with a sum discrepancy, the acknowledgement with a
 * justification.
 */
@Service
public class BudgetConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(BudgetConfirmationService.class);
    private static final Pattern CODE_PATTERN = Pattern.compile("^\\d+(\\.\\d+)+$");

    private final CondominiumRepository condominiums;
    private final BudgetRepository budgets;
    private final BudgetLineRepository lines;
    private final BudgetFundLinkRepository fundLinks;
    private final BudgetEventRepository events;
    private final SourceFileRepository files;
    private final FundRepository funds;
    private final BudgetQueryService query;
    private final BudgetReserveFundService reserveFund;
    private final FindingSyncService findings;
    private final ServicoRubricas budgetItems;
    private final ApplicationEventPublisher publisher;

    public BudgetConfirmationService(CondominiumRepository condominiums, BudgetRepository budgets,
            BudgetLineRepository lines, BudgetFundLinkRepository fundLinks, BudgetEventRepository events,
            SourceFileRepository files, FundRepository funds, BudgetQueryService query,
                    BudgetReserveFundService reserveFund,
            FindingSyncService findings, ServicoRubricas rubricas, ApplicationEventPublisher publisher) {
        this.condominiums = condominiums;
        this.budgets = budgets;
        this.lines = lines;
        this.fundLinks = fundLinks;
        this.events = events;
        this.files = files;
        this.funds = funds;
        this.query = query;
        this.reserveFund = reserveFund;
        this.findings = findings;
        this.budgetItems = rubricas;
        this.publisher = publisher;
    }

    @Transactional
    public BudgetDetailResponse confirm(UUID condominiumId, UUID budgetId, BudgetConfirmationRequest request,
            String username) {
        Condominium condominium = condominiums.lockById(condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        Budget budget = budgets.findByIdAndCondominiumId(budgetId, condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO não encontrada"));
        if (budget.getStatus().isLocked()) {
            throw new BudgetConfirmationRejectedException(HttpStatus.CONFLICT, List.of("A PO já foi confirmada."));
        }
        List<BudgetLine> budgetLines = lines.findByBudgetIdOrderByPosition(budget.getId());
        BudgetStructure structure = BudgetStructure.of(budgetLines);
        var assessment = query.assess(budget, structure);

        List<String> reasons = new ArrayList<>();
        YearMonth start = month(request.fiscalYearStart(), "início", reasons);
        YearMonth end = month(request.fiscalYearEnd(), "fim", reasons);
        if (start != null && end != null && end.isBefore(start)) {
            reasons.add("O fim do exercício (" + end + ") é anterior ao início (" + start + ").");
        }
        SourceFile minutes = validateMinutes(condominiumId, request, reasons);
        validateDiscrepancy(budget, request, assessment, reasons);
        Map<UUID, String> newCodes = validateCodes(budgetLines, request.effectiveCodes(), reasons);
        Map<BudgetLine, Fund> links = validateFunds(condominium, structure, request.funds(), reasons);
        if (!reasons.isEmpty()) {
            throw new BudgetConfirmationRejectedException(HttpStatus.UNPROCESSABLE_CONTENT, reasons);
        }

        Instant now = Instant.now();
        List<Budget> supersededBudgets = overlapping(condominiumId, budget, start, end, request.reapproval());
        int version = supersededBudgets.stream().map(Budget::getVersion).filter(Objects::nonNull)
                .max(Integer::compare).orElse(0) + 1;
        for (Budget previous : supersededBudgets) {
            previous.supersedeFrom(start);
            budgets.save(previous);
            events.save(new BudgetEvent(previous, BudgetEvent.SUPERSEDED, username, now, null,
                    "Substituída a partir de " + start + " pela versão " + version + " (PO " + budget.getId() + ")."));
        }

        Map<UUID, BudgetLine> byId = budgetLines.stream().collect(Collectors.toMap(BudgetLine::getId,
                Function.identity()));
        List<String> changes = new ArrayList<>();
        newCodes.forEach((id, code) -> {
            BudgetLine l = byId.get(id);
            changes.add("%s (ordem %d, %s) → %s".formatted(l.getPrintedCode(), l.getPosition(), l.getDescription(),
                    code));
            l.setEffectiveCode(code);
        });
        lines.saveAll(budgetLines);
        links.forEach((line, fund) -> fundLinks.save(new BudgetFundLink(budget.getId(), line.getId(), fund.getId())));

        boolean acknowledged = budget.getStatus() == BudgetStatus.LIDA_COM_DIVERGENCIA;
        String justification = acknowledged ? request.justification().trim() : null;
        budget.confirm(version, start, end, minutes == null ? null : minutes.getId(), request.withoutMinutes(),
                request.approvalDate(),
                acknowledged, justification, username, now);
        budgets.save(budget);
        events.save(new BudgetEvent(budget, BudgetEvent.CONFIRMED, username, now, justification,
                eventDetail(budget, minutes, changes, links, assessment, supersededBudgets)));

        shortenExtensions(budget, start, end, version, supersededBudgets, username, now);
        recordReserveFundFinding(budget, structure);
        // Budget items (RF-11.7): the condominium's first budget becomes the catalog; on the others, only suggestions
        budgetItems.aoConfirmar(budget, username, now);
        // Budget confirmed and funds linked: the findings of the fiscal year's months are recalculated after the commit
        publisher.publishEvent(BudgetChanged.of(condominiumId, "PO versão " + version + " confirmada (exercício "
                + start + " a " + end + ")", username, now));
        log.info("PO {} confirmada: condominio={} versao={} exercicio={} a {} por={} ciente={}", budget.getId(),
                condominiumId, version, start, end, username, acknowledged);
        return query.detail(budget);
    }

    private static YearMonth month(String text, String which, List<String> reasons) {
        if (text == null || text.isBlank()) {
            reasons.add("Informe o " + which + " do exercício (AAAA-MM).");
            return null;
        }
        try {
            return YearMonth.parse(text.trim());
        } catch (DateTimeParseException e) {
            reasons.add("O " + which + " do exercício deve estar no formato AAAA-MM: " + text);
            return null;
        }
    }

    private SourceFile validateMinutes(UUID condominiumId, BudgetConfirmationRequest request, List<String> reasons) {
        if (request.withoutMinutes() && request.minutesFileId() != null) {
            reasons.add("Informe a ata ou marque \"sem ata\", não os dois.");
            return null;
        }
        if (!request.withoutMinutes() && request.minutesFileId() == null) {
            reasons.add("Informe a ata que aprovou a PO ou marque \"sem ata\".");
            return null;
        }
        if (request.withoutMinutes()) {
            return null;
        }
        SourceFile minutes = files.findByIdAndCondominiumId(request.minutesFileId(), condominiumId).orElse(null);
        if (minutes == null) {
            reasons.add("A ata informada não é um arquivo deste condomínio.");
        } else if (minutes.getCategory() != FileCategory.ATA) {
            reasons.add("O arquivo \"" + minutes.getOriginalName() + "\" não está na categoria \"" + FileCategory.ATA.label()
                    + "\".");
        }
        if (request.approvalDate() == null) {
            reasons.add("Informe a data da assembleia que aprovou a PO.");
        }
        return minutes;
    }

    /** Q29: a budget read with a sum discrepancy is only confirmed "aware of the discrepancy", with a justification. */
    private static void validateDiscrepancy(Budget budget, BudgetConfirmationRequest request,
            BudgetReadingAssessment.Result assessment, List<String> reasons) {
        if (budget.getStatus() != BudgetStatus.LIDA_COM_DIVERGENCIA) {
            return;
        }
        String sums = String.join("; ", assessment.discrepancies());
        if (!request.discrepancyAcknowledged()) {
            reasons.add("A PO foi lida com divergência (" + sums + "). Para confirmar, marque \"ciente da"
                    + " divergência\" e informe a justificativa; os cálculos usam a soma das linhas.");
        } else if (request.justification() == null || request.justification().isBlank()) {
            reasons.add("A confirmação ciente da divergência exige justificativa.");
        }
    }

    /**
     * Effective codes: only for lines whose printed code repeats, in the same group and with the same number of levels.
     * Once applied, no effective code may repeat.
     */
    private static Map<UUID, String> validateCodes(List<BudgetLine> budgetLines, List<EffectiveCodeRequest> requests,
            List<String> reasons) {
        Map<UUID, BudgetLine> byId = budgetLines.stream().collect(Collectors.toMap(BudgetLine::getId,
                Function.identity()));
        Map<String, Long> printedCodes = budgetLines.stream()
                .collect(Collectors.groupingBy(BudgetLine::getPrintedCode, Collectors.counting()));
        Map<UUID, String> effectiveByLine = new LinkedHashMap<>();
        for (EffectiveCodeRequest c : requests) {
            BudgetLine l = c.lineId() == null ? null : byId.get(c.lineId());
            String code = c.code() == null ? "" : c.code().trim();
            if (l == null) {
                reasons.add("Código efetivo para uma linha que não é desta PO.");
            } else if (printedCodes.get(l.getPrintedCode()) < 2) {
                reasons.add("O código da linha " + l.getPrintedCode() + " não se repete e não pode ser trocado.");
            } else if (!CODE_PATTERN.matcher(code).matches() || !sameGroup(l.getPrintedCode(), code)) {
                reasons.add("Código efetivo \"" + code + "\" inválido para a linha " + l.getPrintedCode()
                        + " (ordem " + l.getPosition() + "): use um código do mesmo grupo, como "
                        + parent(l.getPrintedCode()) + ".N.");
            } else if (effectiveByLine.put(l.getId(), code) != null) {
                reasons.add("Mais de um código efetivo para a linha " + l.getPrintedCode() + " (ordem "
                        + l.getPosition() + ").");
            }
        }
        Map<String, List<BudgetLine>> linesByCode = new LinkedHashMap<>();
        budgetLines.forEach(l -> linesByCode.computeIfAbsent(effectiveByLine.getOrDefault(l.getId(), l.getEffectiveCode()),
                k -> new ArrayList<>()).add(l));
        linesByCode.forEach((code, sameCode) -> {
            if (sameCode.size() > 1) {
                reasons.add("Código repetido sem código efetivo distinto: " + code + " (ordens " + sameCode.stream()
                        .map(l -> String.valueOf(l.getPosition())).collect(Collectors.joining(", "))
                        + "). Informe um código distinto para a linha repetida; código repetido não é divergência de"
                        + " soma.");
            }
        });
        return effectiveByLine;
    }

    private static boolean sameGroup(String printed, String effective) {
        return parent(printed).equals(parent(effective)) && printed.split("\\.").length == effective.split("\\.").length;
    }

    private static String parent(String code) {
        int dot = code.lastIndexOf('.');
        return dot < 0 ? "" : code.substring(0, dot);
    }

    /** Each fund line linked to a distinct cash flow fund that is not the operating fund (Condomínio). */
    private Map<BudgetLine, Fund> validateFunds(Condominium condominium, BudgetStructure structure,
            List<FundLinkRequest> requests,
            List<String> reasons) {
        List<BudgetLine> fundLines = structure.funds().map(BudgetStructure.Group::lines).orElse(List.of());
        Map<UUID, BudgetLine> byId = fundLines.stream().collect(Collectors.toMap(BudgetLine::getId,
                Function.identity()));
        Map<UUID, Fund> ofCondominium = funds.findByCondominiumId(condominium.getId()).stream()
                .collect(Collectors.toMap(Fund::getId, Function.identity()));
        Map<BudgetLine, Fund> links = new LinkedHashMap<>();
        Set<UUID> used = new HashSet<>();
        for (FundLinkRequest f : requests) {
            BudgetLine line = f.lineId() == null ? null : byId.get(f.lineId());
            Fund fund = f.fundId() == null ? null : ofCondominium.get(f.fundId());
            if (line == null) {
                reasons.add("Ligação de fundo para uma linha que não é linha de fundo desta PO.");
            } else if (fund == null) {
                reasons.add("O fundo informado para a linha " + line.getPrintedCode() + " não é deste condomínio.");
            } else if (fund.getId().equals(condominium.getOperatingFundId())) {
                reasons.add("O fundo \"" + fund.getName() + "\" é o fundo ordinário e não pode ser ligado à linha "
                        + line.getPrintedCode() + ".");
            } else if (!used.add(fund.getId())) {
                reasons.add("O fundo \"" + fund.getName() + "\" foi ligado a mais de uma linha de fundo.");
            } else if (links.put(line, fund) != null) {
                reasons.add("Mais de um fundo para a linha " + line.getPrintedCode() + ".");
            }
        }
        fundLines.stream().filter(l -> !links.containsKey(l) && requests.stream()
                .noneMatch(p -> l.getId().equals(p.lineId())))
                .forEach(l -> reasons.add("Ligue a linha " + l.getPrintedCode() + " " + l.getDescription()
                        + " a um fundo do fluxo."));
        return links;
    }

    /** One budget per month: overlap only with a reapproval, which covers until the end of the superseded budget. */
    private List<Budget> overlapping(UUID condominiumId, Budget budget, YearMonth start,
            YearMonth end, boolean reapproval) {
        List<BudgetValidity> conflicts = budgets.findByCondominiumIdAndStatusIn(condominiumId,
                        EnumSet.of(BudgetStatus.CONFIRMADA, BudgetStatus.SUBSTITUIDA)).stream()
                .filter(p -> !p.getId().equals(budget.getId()))
                .map(BudgetValidity::of).flatMap(java.util.Optional::stream)
                .filter(v -> v.overlaps(start, end)).toList();
        if (conflicts.isEmpty()) {
            return List.of();
        }
        String list = conflicts.stream().map(v -> "versão " + v.budget().getVersion() + " (" + v.period() + ")")
                .collect(Collectors.joining(", "));
        if (!reapproval) {
            throw new BudgetConfirmationRejectedException(HttpStatus.CONFLICT, List.of("Já existe PO confirmada para "
                    + "meses deste exercício: " + list + ". Só uma PO vale para cada mês; se esta é uma reaprovação,"
                    + " confirme como reaprovação."));
        }
        List<String> reasons = conflicts.stream().filter(v -> end.isBefore(v.end()))
                .map(v -> "A reaprovação precisa cobrir até " + v.end() + ", fim da versão " + v.budget().getVersion()
                        + " que ela substitui.")
                .toList();
        if (!reasons.isEmpty()) {
            throw new BudgetConfirmationRejectedException(HttpStatus.CONFLICT, reasons);
        }
        return conflicts.stream().map(BudgetValidity::budget).toList();
    }

    /**
     * RF-11.3 and ADR 0005, Decision 4: confirming a budget over extended months is allowed, and the other budget's
     * extension ends in the month before this one starts. The budget superseded by a reapproval loses its extension
     * (only the valid version can be extended). Automatic event in the trail of the budget that changed.
     */
    private void shortenExtensions(Budget budget, YearMonth start, YearMonth end, int version,
            List<Budget> supersededBudgets, String username, Instant now) {
        String reason = "PO " + start.getYear() + "/" + end.getYear() + " confirmada (versão " + version + ", "
                + start + " a " + end + ")";
        for (Budget other : budgets.findByCondominiumIdAndStatusIn(budget.getCondominiumId(),
                EnumSet.of(BudgetStatus.CONFIRMADA, BudgetStatus.SUBSTITUIDA))) {
            YearMonth until = other.getExtendedUntil();
            if (other.getId().equals(budget.getId()) || until == null) {
                continue;
            }
            YearMonth first = other.getFiscalYearEnd().plusMonths(1);
            boolean superseded = supersededBudgets.stream().anyMatch(s -> s.getId().equals(other.getId()));
            if (!superseded && (until.isBefore(start) || first.isAfter(end))) {
                continue;
            }
            String before = first + " a " + until;
            if (superseded) {
                other.undoExtension();
            } else {
                other.shortenExtension(start.minusMonths(1));
            }
            budgets.save(other);
            String after = other.getExtendedUntil() == null ? "sem prorrogação"
                    : "prorrogada de " + first + " a " + other.getExtendedUntil();
            events.save(new BudgetEvent(other, BudgetEvent.EXTENSION_SHORTENED, username, now, reason,
                    "Prorrogação " + before + " → " + after + "."));
        }
    }

    private void recordReserveFundFinding(Budget budget, BudgetStructure structure) {
        if (!(reserveFund.assess(budget,
                structure) instanceof BudgetReserveFundService.Assessed a) || !a.assessment().aboveCap()) {
            return;
        }
        BudgetLine l = a.line();
        String description = ("Fundo de reserva previsto na PO (linha %s): %s por mês, %s do previsto do mês (%s). "
                + "O teto da Conv. 20.1 é %s. Verificar a ata que aprovou a PO.").formatted(l.getEffectiveCode(),
                formatar(l.getBudgeted()), a.assessment().displayPercentage(), formatar(budget.getMonthlyPlanned()),
                a.assessment().displayCap());
        findings.register(budget.getCondominiumId(), ReserveFundCapRule.CODE, ReserveFundCapRule.VERSION,
                ReserveFundCapRule.SEVERITY, budget.getFiscalYearStart(), target(budget, l), description,
                List.of(new Evidence(budget.getFileId(), budget.getSha256(), l.getPage(),
                        "PO, linha %s %s (ordem %d): %s".formatted(l.getEffectiveCode(), l.getDescription(),
                                l.getPosition(), formatar(l.getBudgeted())), l.getId())));
    }

    static String target(Budget budget, BudgetLine line) {
        return targetPrefix(budget) + "linha:" + line.getId();
    }

    static String targetPrefix(Budget budget) {
        return "previsao:" + budget.getId() + ":";
    }

    private static String eventDetail(Budget budget, SourceFile minutes, List<String> changes,
            Map<BudgetLine, Fund> links, BudgetReadingAssessment.Result assessment,
            List<Budget> supersededBudgets) {
        List<String> parts = new ArrayList<>();
        parts.add("Exercício " + budget.getFiscalYearStart() + " a " + budget.getFiscalYearEnd() + "; versão " + budget.getVersion());
        parts.add(minutes == null ? "Sem ata (pendência de implantação)"
                : "Ata: " + minutes.getOriginalName() + " (" + minutes.getSha256() + "), aprovada em " + budget.getApprovalDate());
        parts.add("Códigos efetivos: " + (changes.isEmpty() ? "nenhuma troca" : String.join("; ", changes)));
        parts.add("Fundos: " + (links.isEmpty() ? "nenhuma linha de fundo" : links.entrySet().stream()
                .map(e -> e.getKey().getPrintedCode() + " " + e.getKey().getDescription() + " → " + e.getValue().getName())
                .collect(Collectors.joining("; "))));
        if (!assessment.discrepancies().isEmpty()) {
            parts.add("Conferências que falharam (confirmada ciente da divergência): "
                    + String.join("; ", assessment.discrepancies()));
        }
        if (!assessment.roundings().isEmpty()) {
            parts.add("Diferenças tratadas como arredondamento: " + String.join("; ", assessment.roundings()));
        }
        if (!supersededBudgets.isEmpty()) {
            Map<UUID, Integer> versions = new HashMap<>();
            supersededBudgets.forEach(s -> versions.put(s.getId(), s.getVersion()));
            parts.add("Reaprovação: substitui a partir de " + budget.getFiscalYearStart() + " " + versions.entrySet()
                    .stream().map(e -> "a versão " + e.getValue() + " (PO " + e.getKey() + ")")
                    .collect(Collectors.joining(", ")));
        }
        return String.join("\n", parts);
    }
}
