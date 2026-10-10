package br.com.condominioauditoria.api.service.budget;

import br.com.condominioauditoria.api.dto.response.budget.BudgetCheckResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetConfirmationResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetDetailResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetEventResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetFindingResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetFundLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetSummaryResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetWarningResponse;
import br.com.condominioauditoria.api.dto.response.budget.RepeatedCodeResponse;
import br.com.condominioauditoria.api.dto.response.budget.RepeatedLineResponse;
import br.com.condominioauditoria.api.mapper.BudgetMapper;
import br.com.condominioauditoria.api.model.accounting.Fund;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.model.enums.BudgetWarningCode;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.accounting.FundRepository;
import br.com.condominioauditoria.api.repository.accounting.TotalsCheckRepository;
import br.com.condominioauditoria.api.repository.audit.FindingRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetEventRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetFundLinkRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetLineRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.service.calculator.BudgetReadingAssessment;
import br.com.condominioauditoria.api.service.calculator.BudgetReadingAssessment.BudgetCheck;
import br.com.condominioauditoria.api.service.calculator.BudgetStructure;
import br.com.condominioauditoria.api.service.calculator.BudgetValidity;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Budget reading for the API. Warnings are computed here from what is saved (ADR 0004, Decision 3: warnings that are
 * not findings have no table), with the tolerance saved on the budget itself: reading always repeats saving.
 */
@Service
public class BudgetQueryService {

    private final BudgetRepository budgets;
    private final BudgetLineRepository lines;
    private final TotalsCheckRepository totalsChecks;
    private final SourceFileRepository files;
    private final BudgetFundLinkRepository fundLinks;
    private final FundRepository funds;
    private final FindingRepository findings;
    private final BudgetReserveFundService reserveFund;
    private final BudgetEventRepository events;

    public BudgetQueryService(BudgetRepository budgets, BudgetLineRepository lines,
            TotalsCheckRepository totalsChecks, SourceFileRepository files, BudgetFundLinkRepository fundLinks,
            FundRepository funds, FindingRepository findings, BudgetReserveFundService reserveFund,
            BudgetEventRepository events) {
        this.budgets = budgets;
        this.lines = lines;
        this.totalsChecks = totalsChecks;
        this.files = files;
        this.fundLinks = fundLinks;
        this.funds = funds;
        this.findings = findings;
        this.reserveFund = reserveFund;
        this.events = events;
    }

    /**
     * The budget valid in the month (RF-03.1.3), by fiscal year or by extension (RF-11.3), or empty: "sem PO aprovada
     * para este mês".
     */
    @Transactional(readOnly = true)
    public Optional<Budget> activeInMonth(UUID condominiumId, YearMonth month) {
        return BudgetValidity.activeInMonth(budgets.findByCondominiumIdAndStatusIn(condominiumId,
                EnumSet.of(BudgetStatus.CONFIRMED, BudgetStatus.SUPERSEDED)), month);
    }

    @Transactional(readOnly = true)
    public List<BudgetSummaryResponse> list(UUID condominiumId) {
        return budgets.findByCondominiumIdOrderByReadAtDesc(condominiumId).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public Optional<BudgetDetailResponse> detail(UUID condominiumId, UUID id) {
        return budgets.findByIdAndCondominiumId(id, condominiumId).map(this::detail);
    }

    /** The budget's audit trail (every role): confirmation, supersession and fund link changes. Empty = not found. */
    @Transactional(readOnly = true)
    public Optional<List<BudgetEventResponse>> events(UUID condominiumId, UUID budgetId) {
        return budgets.findByIdAndCondominiumId(budgetId, condominiumId).map(budget -> events
                .findByBudgetIdOrderByOccurredAt(budget.getId()).stream().map(BudgetMapper::toResponse).toList());
    }

    public BudgetDetailResponse detail(Budget p) {
        List<BudgetLine> budgetLines = lines.findByBudgetIdOrderByPosition(p.getId());
        BudgetStructure structure = BudgetStructure.of(budgetLines);
        var assessment = assess(p, structure);
        Set<UUID> fundLines = new HashSet<>();
        structure.funds().ifPresent(f -> f.lines().forEach(l -> fundLines.add(l.getId())));

        List<BudgetCheckResponse> checkResponses = assessment.checks().stream()
                .map(a -> new BudgetCheckResponse(a.check().code(), a.check().description(),
                        a.check().ok(), a.check().detail(), a.classification(), a.explanation()))
                .toList();
        List<RepeatedCodeResponse> repeated = repeatedCodes(budgetLines);
        return new BudgetDetailResponse(summary(p), p.getPreviousBudgetedColumn(), p.getBudgetedColumn(),
                p.getPrintedMonthlyPlanned(), p.getRoundingTolerance(), month(p.getSupersededFrom()),
                p.getStatus().isLocked() ? new BudgetConfirmationResponse(p.getMinutesFileId(), p.isWithoutMinutes(),
                        p.getApprovalDate(), p.isDiscrepancyAcknowledged(), p.getDiscrepancyJustification()) : null,
                budgetLines.stream().map(l -> BudgetMapper.toResponse(l, fundLines.contains(l.getId()))).toList(),
                        checkResponses,
                warnings(p, structure, assessment, repeated), repeated, funds(p, budgetLines), findings(p));
    }

    private List<BudgetFundLineResponse> funds(Budget p, List<BudgetLine> budgetLines) {
        Map<UUID, BudgetLine> byId = budgetLines.stream().collect(Collectors.toMap(BudgetLine::getId,
                Function.identity()));
        Map<UUID, String> names = funds.findByCondominiumId(p.getCondominiumId()).stream()
                .collect(Collectors.toMap(Fund::getId, Fund::getName));
        return fundLinks.findByBudgetId(p.getId()).stream()
                .map(f -> {
                    BudgetLine l = byId.get(f.getBudgetLineId());
                    return new BudgetFundLineResponse(l.getId(), l.getEffectiveCode(), l.getDescription(),
                            l.getBudgeted(),
                            f.getFundId(), names.get(f.getFundId()));
                })
                .sorted(java.util.Comparator.comparing(BudgetFundLineResponse::effectiveCode))
                .toList();
    }

    private List<BudgetFindingResponse> findings(Budget p) {
        return findings.findByCondominiumIdAndTargetStartingWithOrderByCreatedAt(p.getCondominiumId(),
                        BudgetConfirmationService.targetPrefix(p)).stream()
                .map(a -> new BudgetFindingResponse(a.getId(), a.getRule(), a.getRuleVersion(), a.getSeverity().name(),
                        a.getReferenceMonth().toString(), a.getDescription(), a.getStatus().name()))
                .toList();
    }

    BudgetReadingAssessment.Result assess(Budget p, BudgetStructure structure) {
        List<BudgetCheck> saved = totalsChecks.findByFileIdOrderBySequence(p.getFileId()).stream()
                .map(c -> new BudgetCheck(c.getCode(), c.getDescription(), c.isOk(), c.getDetail())).toList();
        return BudgetReadingAssessment.assess(structure, saved, p.getRoundingTolerance());
    }

    private List<BudgetWarningResponse> warnings(Budget p, BudgetStructure structure,
            BudgetReadingAssessment.Result assessment, List<RepeatedCodeResponse> repeated) {
        List<BudgetWarningResponse> warnings = new ArrayList<>();
        if (p.getStatus().isLocked()) {
            warnings.addAll(confirmationWarnings(p, structure, assessment));
        }
        assessment.roundings().forEach(t -> warnings.add(new BudgetWarningResponse(BudgetWarningCode.ROUNDING,
                t)));
        if (p.getStatus() == BudgetStatus.READ_WITH_DISCREPANCY) {
            assessment.discrepancies().forEach(t -> warnings.add(new BudgetWarningResponse(BudgetWarningCode.DISCREPANCY,
                    "PO lida com divergência: " + t + ". A PO não é usada no previsto × realizado até a confirmação.")));
        }
        repeated.stream().filter(r -> !r.resolved()).forEach(r -> warnings.add(new BudgetWarningResponse(BudgetWarningCode.REPEATED_CODE,
                "Código " + r.printedCode() + " impresso " + r.lines().size()
                        + " vezes: informe um código distinto na confirmação")));
        return warnings;
    }

    /**
     * Informational warnings of the confirmed budget, never findings: discrepancy acknowledgement (Q29), approval
     * outside the 1st quarter (Conv. 10.2), fiscal year without minutes and a rule that could not be assessed.
     */
    private List<BudgetWarningResponse> confirmationWarnings(Budget p, BudgetStructure structure,
            BudgetReadingAssessment.Result assessment) {
        List<BudgetWarningResponse> warnings = new ArrayList<>();
        if (p.isDiscrepancyAcknowledged()) {
            warnings.add(new BudgetWarningResponse(BudgetWarningCode.CONFIRMED_WITH_DISCREPANCY,
                    "PO confirmada com divergência: " + String.join("; ", assessment.discrepancies())));
        }
        if (outsideFirstQuarter(p)) {
            warnings.add(new BudgetWarningResponse(BudgetWarningCode.OUTSIDE_FIRST_QUARTER,
                    OUTSIDE_FIRST_QUARTER_TEXT));
        }
        if (p.isWithoutMinutes()) {
            warnings.add(new BudgetWarningResponse(BudgetWarningCode.NO_MINUTES,
                    "Exercício informado sem ata: pendência de implantação"));
        }
        if (reserveFund.assess(p, structure) instanceof BudgetReserveFundService.NotAssessed n) {
            warnings.add(new BudgetWarningResponse(BudgetWarningCode.RULE_NOT_EVALUATED, n.reason()));
        }
        return warnings;
    }

    static final String OUTSIDE_FIRST_QUARTER_TEXT = "PO aprovada fora do 1º trimestre (Conv. 10.2)";

    /** Approval month: the meeting date; without it, the fiscal year start (which comes from the minutes date). */
    public static boolean outsideFirstQuarter(Budget p) {
        int month = p.getApprovalDate() != null ? p.getApprovalDate().getMonthValue()
                : p.getFiscalYearStart().getMonthValue();
        return month > 3;
    }

    /** Codes printed more than once; resolved when the effective codes of those lines are already distinct. */
    static List<RepeatedCodeResponse> repeatedCodes(List<BudgetLine> budgetLines) {
        Map<String, List<BudgetLine>> byCode = new LinkedHashMap<>();
        budgetLines.forEach(l -> byCode.computeIfAbsent(l.getPrintedCode(), c -> new ArrayList<>()).add(l));
        Map<String, Long> countByCode = new LinkedHashMap<>();
        budgetLines.forEach(l -> countByCode.merge(l.getEffectiveCode(), 1L, Long::sum));
        return byCode.entrySet().stream().filter(e -> e.getValue().size() > 1)
                .map(e -> new RepeatedCodeResponse(e.getKey(),
                        e.getValue().stream().allMatch(l -> countByCode.get(l.getEffectiveCode()) == 1),
                        e.getValue().stream().map(l -> new RepeatedLineResponse(l.getId(), l.getPosition(),
                                l.getDescription(),
                                l.getEffectiveCode())).toList()))
                .toList();
    }

    BudgetSummaryResponse summary(Budget p) {
        String name = files.findById(p.getFileId()).map(SourceFile::getOriginalName).orElse(null);
        return new BudgetSummaryResponse(p.getId(), p.getFileId(), name, p.getSha256(), p.getStatus(), p.getVersion(),
                p.getTitle(), p.getPrintedFiscalYear(), month(p.getFiscalYearStart()), month(p.getFiscalYearEnd()),
                p.getPrintedTotal(), p.getMonthlyPlanned(), p.getReadAt(), p.getConfirmedBy(), p.getConfirmedAt(),
                BudgetMapper.toExtensionResponse(p));
    }

    public static String month(YearMonth m) {
        return m == null ? null : m.toString();
    }
}
