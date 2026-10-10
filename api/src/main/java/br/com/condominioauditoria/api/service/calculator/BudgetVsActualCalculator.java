package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.dto.response.budget.BlockAccountResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetBriefResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualGroupResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualTotalsResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualWarningResponse;
import br.com.condominioauditoria.api.dto.response.budget.CashFlowCheckResponse;
import br.com.condominioauditoria.api.dto.response.budget.EntryBlockResponse;
import br.com.condominioauditoria.api.dto.response.budget.EvidenceResponse;
import br.com.condominioauditoria.api.dto.response.budget.FiscalYearMonthResponse;
import br.com.condominioauditoria.api.dto.response.budget.FundResultResponse;
import br.com.condominioauditoria.api.dto.response.budget.OverrunLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.PeriodMappingSummaryResponse;
import br.com.condominioauditoria.api.dto.response.budget.Rule20Response;
import br.com.condominioauditoria.api.dto.response.budget.UsedCashFlowResponse;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.accounting.LedgerEntryFingerprint;
import br.com.condominioauditoria.api.model.budget.AccountMapping;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.MappingTarget;
import br.com.condominioauditoria.api.model.enums.AccountMappingStatus;
import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import br.com.condominioauditoria.api.model.enums.FundComparisonStatus;
import br.com.condominioauditoria.api.model.enums.MappingTargetType;
import br.com.condominioauditoria.api.model.enums.MonthStatus;
import br.com.condominioauditoria.api.service.audit.rule.MonthlyOverrunRule;
import br.com.condominioauditoria.api.service.budget.AccountMappingService;
import br.com.condominioauditoria.api.service.budget.BudgetQueryService;
import br.com.condominioauditoria.api.util.MoneyFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Budget vs. actual (RF-03.1.6 to RF-03.1.11; ADR 0004, Decision 5). <b>Pure function</b>: no database, clock or
 * network access. Same input and same {@link #VERSION} = same result. The screen, the export and the golden test use
 * this function.
 *
 * <p>Rules (answers Q18 to Q26 and Q30):
 * <ul>
 * <li>month = month of the entry date; amount = the entry's debit as it is in the cash flow;</li>
 * <li>actual = debits of the Condomínio fund (confirmed operating fund), through the <b>confirmed</b> account mapping:
 * budget line; ADJUSTMENT goes to "ajustes" (not an expense); TO_REALLOCATE goes to "a realocar" until there is a
 * reallocation; TRANSFER and entries marked as transfers between funds are left out; an account without a
 * confirmed mapping goes to "sem linha da PO" and is never added to another line;</li>
 * <li>line planned = budgeted (monthly, the same in every month); month planned = sum of the lines of the expense
 * groups (Q30), never the printed total;</li>
 * <li>funds linked to the 1.9 lines: collection = fee receipt credits (Q25), never debits;</li>
 * <li>a month without a loaded cash flow does not become zero; two cash flows in the same month are not summed;</li>
 * <li>the 20% rule through {@link MonthlyOverrunRule}; percentages with 10 decimals, shown with 1 (half up).</li>
 * </ul>
 */
public final class BudgetVsActualCalculator {

    /**
     * Version of this calculation's rules; changes whenever any rule above changes. Goes in every result. Version 2:
     * the reallocation matches the entry by the stable key (fingerprint), not by the id.
     */
    public static final String VERSION = "2";

    public static final String TARGET_ADJUSTMENTS = "ADJUSTMENTS";
    public static final String TARGET_TO_REALLOCATE = "TO_REALLOCATE";
    public static final String TARGET_WITHOUT_BUDGET_LINE = "NO_BUDGET_LINE";
    public static final String TARGET_TRANSFERS = "TRANSFERS";
    /** Evidence of the whole actual expense: budget lines + to reallocate + without budget line (RF-03.1.12). */
    public static final String TARGET_TOTAL = "total";

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String[] MONTH_NAMES = {"jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out",
            "nov",
            "dez"};

    public sealed interface Period permits Month, Cumulative {
    }

    public record Month(YearMonth month) implements Period {
    }

    /** From the start of the fiscal year to the last month with a loaded cash flow. */
    public record Cumulative() implements Period {
    }

    /** Completed file of the trial balances and cash flows category, with the period read. */
    public record CashFlowFile(UUID fileId, String name, String sha256, LocalDate periodStart, LocalDate periodEnd,
            Instant uploadedAt, String uploadedBy) {

        public boolean covers(YearMonth month) {
            return periodStart != null && periodEnd != null && !periodStart.isAfter(month.atEndOfMonth())
                    && !periodEnd.isBefore(month.atDay(1));
        }

        public UsedCashFlowResponse used() {
            return new UsedCashFlowResponse(fileId, name, sha256, periodStart, periodEnd, uploadedAt, uploadedBy);
        }
    }

    /**
     * "A realocar" entry moved to a budget line (RF-03.1.7). Matches the entry by the {@code key} of the
     * {@link LedgerEntryFingerprint}, which survives the cash flow reprocessing; date, account, amount, file and page
     * serve the warning when there is no match.
     */
    public record ReallocatedEntry(UUID id, String key, UUID fileId, LocalDate date, String account, BigDecimal amount,
            int page, UUID budgetLineId, String username, Instant at) {

        public ReallocatedEntry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(budgetLineId, "budgetLineId");
        }
    }

    /**
     * @param fundByLine 1.9.x line → cash flow fund linked by the Admin
     * @param overrunLimitPercentage parameter of Conv. 16.2 in force in the period (null: rule not assessed)
     * @param budgetWarnings warnings of the budget itself (rounding, confirmed with a mismatch), repeated in the result
     */
    public record Input(Budget budget, String budgetFileName, List<BudgetLine> lines, List<AccountMapping> mappings,
            Map<UUID, UUID> fundByLine, Map<UUID, String> fundNames, UUID operatingFundId, List<CashFlowFile> cashFlows,
            List<LedgerEntry> entries, List<ReallocatedEntry> reallocations, BigDecimal overrunLimitPercentage,
            List<BudgetVsActualWarningResponse> budgetWarnings, Period period) {

        public Input {
            Objects.requireNonNull(period, "period");
            lines = lines == null ? List.of() : List.copyOf(lines);
            mappings = mappings == null ? List.of() : List.copyOf(mappings);
            fundByLine = fundByLine == null ? Map.of() : Map.copyOf(fundByLine);
            fundNames = fundNames == null ? Map.of() : Map.copyOf(fundNames);
            cashFlows = cashFlows == null ? List.of() : List.copyOf(cashFlows);
            entries = entries == null ? List.of() : List.copyOf(entries);
            reallocations = reallocations == null ? List.of() : List.copyOf(reallocations);
            budgetWarnings = budgetWarnings == null ? List.of() : List.copyOf(budgetWarnings);
        }
    }

    /** Result and the entries of each number, by target ("line:&lt;id&gt;", "fund:&lt;id&gt;", ADJUSTMENTS...). */
    public record Calculation(BudgetVsActualResponse result, Map<String, List<EvidenceResponse>> evidence) {
    }

    private BudgetVsActualCalculator() {
    }

    public static String lineTarget(UUID lineId) {
        return "line:" + lineId;
    }

    public static String fundTarget(UUID fundId) {
        return "fund:" + fundId;
    }

    /** Evidence of a budget group: the entries of its lines (by the group line id). */
    public static String groupTarget(UUID groupLineId) {
        return "group:" + groupLineId;
    }

    /**
     * Entries that make up a number (RF-03.1.12): "line:&lt;id&gt;", "group:&lt;id&gt;" (the group lines, in budget
     * order), "total" (actual expense: the lines of all groups, then to reallocate and without budget line),
     * "fund:&lt;id&gt;" and the blocks (ADJUSTMENTS, TO_REALLOCATE, NO_BUDGET_LINE, TRANSFERS). Target without entries:
     * empty list. Only reads what the calculation already assessed.
     */
    public static List<EvidenceResponse> evidence(Calculation c, String target) {
        String a = target == null ? "" : target.trim();
        BudgetVsActualResponse r = c.result();
        if (a.startsWith("group:")) {
            return r.groups().stream().filter(g -> groupTarget(g.lineId()).equals(a)).findFirst()
                    .map(g -> ofGroup(c, g)).orElse(List.of());
        }
        if (a.equals(TARGET_TOTAL)) {
            List<EvidenceResponse> all = new ArrayList<>();
            r.groups().forEach(g -> all.addAll(ofGroup(c, g)));
            all.addAll(c.evidence().getOrDefault(TARGET_TO_REALLOCATE, List.of()));
            all.addAll(c.evidence().getOrDefault(TARGET_WITHOUT_BUDGET_LINE, List.of()));
            return List.copyOf(all);
        }
        return c.evidence().getOrDefault(a, List.of());
    }

    private static List<EvidenceResponse> ofGroup(Calculation c, BudgetVsActualGroupResponse g) {
        List<EvidenceResponse> list = new ArrayList<>();
        g.lines().forEach(l -> list.addAll(c.evidence().getOrDefault(lineTarget(l.lineId()), List.of())));
        return List.copyOf(list);
    }

    public static Calculation calculate(Input e) {
        String period = e.period() instanceof Month m ? m.month().toString() : "cumulative";
        Budget budget = e.budget();
        if (budget == null) {
            return empty(period, BudgetVsActualStatus.NO_BUDGET, e.period() instanceof Month m
                    ? "Sem PO aprovada para " + mmyyyy(m.month()) : "Sem PO aprovada", null, List.of(), e);
        }
        BudgetBriefResponse budgetBrief = new BudgetBriefResponse(budget.getId(), budget.getVersion(),
                budget.getStatus(), budget.getFileId(),
                e.budgetFileName(), budget.getSha256(), BudgetQueryService.month(budget.getFiscalYearStart()),
                BudgetQueryService.month(budget.getFiscalYearEnd()));
        BudgetValidity validity = BudgetValidity.of(budget).orElse(null);
        if (validity == null) {
            return empty(period, BudgetVsActualStatus.BUDGET_NOT_CONFIRMED,
                    "PO não confirmada: o Admin confirma a PO antes do"
                    + " previsto × realizado", budgetBrief, List.of(), e);
        }
        // Extension (RF-11.3): the month after the fiscal year uses this budget, marked "PO prorrogada"
        BudgetValidity extension = BudgetValidity.extension(budget).orElse(null);
        boolean extendedMonth = e.period() instanceof Month m && !validity.covers(m.month()) && extension != null
                && extension.covers(m.month());
        if (e.period() instanceof Month m && !validity.covers(m.month()) && !extendedMonth) {
            return empty(period, BudgetVsActualStatus.NO_BUDGET, "Sem PO aprovada para " + mmyyyy(m.month()), budgetBrief,
                    List.of(), e);
        }
        if (e.operatingFundId() == null) {
            return empty(period, BudgetVsActualStatus.NO_OPERATING_FUND,
                    "Confirme o fundo ordinário (fundo Condomínio) do"
                    + " condomínio para calcular o realizado", budgetBrief, List.of(), e);
        }
        Context base = new Context(withWarning(e, extensionWarning(budget, validity, extension, e.period(),
                extendedMonth)));

        if (e.period() instanceof Month m) {
            List<CashFlowFile> ofMonth = base.monthCashFlows(m.month());
            if (ofMonth.isEmpty()) {
                return empty(period, BudgetVsActualStatus.NO_CASH_FLOW, "Sem fluxo carregado para " + mmyyyy(m.month()),
                        budgetBrief,
                        List.of(monthWithoutNumbers(m.month(), MonthStatus.NO_CASH_FLOW,
                                ofMonth).withExtended(extendedMonth)),
                        base.input);
            }
            if (ofMonth.size() > 1) {
                return empty(period, BudgetVsActualStatus.TWO_CASH_FLOWS, "Dois fluxos para " + mmyyyy(m.month())
                                + ": substitua, reclassifique ou exclua um", budgetBrief,
                        List.of(monthWithoutNumbers(m.month(), MonthStatus.TWO_CASH_FLOWS,
                                ofMonth).withExtended(extendedMonth)),
                        base.input);
            }
            Tally ap = base.tally(Map.of(m.month(), ofMonth.getFirst()));
            FiscalYearMonthResponse month = base.monthSummary(m.month(), ofMonth.getFirst(),
                    ap).withExtended(extendedMonth);
            return base.build(period, budgetBrief, ap, 1, List.of(month), List.of(m.month().toString()), List.of(),
                    List.of(), true);
        }

        // Fiscal year cumulative (RF-03.1.10): only the months with a cash flow, on both sides
        List<YearMonth> fiscalYear = months(validity.start(), validity.end());
        Map<YearMonth, List<CashFlowFile>> byMonth = new LinkedHashMap<>();
        fiscalYear.forEach(month -> byMonth.put(month, base.monthCashFlows(month)));
        YearMonth last = fiscalYear.stream().filter(month -> !byMonth.get(month).isEmpty()).reduce((a,
                b) -> b).orElse(null);
        Map<YearMonth, CashFlowFile> chosen = new TreeMap<>();
        List<FiscalYearMonthResponse> months = new ArrayList<>();
        List<String> summed = new ArrayList<>();
        List<YearMonth> missing = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        for (YearMonth month : fiscalYear) {
            List<CashFlowFile> f = byMonth.get(month);
            if (f.size() == 1) {
                chosen.put(month, f.getFirst());
                summed.add(month.toString());
                months.add(base.monthSummary(month, f.getFirst(), base.tally(Map.of(month, f.getFirst()))));
            } else if (f.isEmpty()) {
                months.add(monthWithoutNumbers(month, MonthStatus.NO_CASH_FLOW, f));
                if (last != null && month.isBefore(last)) {
                    missing.add(month);
                }
            } else {
                months.add(monthWithoutNumbers(month, MonthStatus.TWO_CASH_FLOWS, f));
                duplicates.add(month.toString());
            }
        }
        // Extended months: after the 12, with each month's numbers, outside the cumulative sum (RF-11.3)
        if (extension != null) {
            for (YearMonth month : months(extension.start(), extension.end())) {
                List<CashFlowFile> f = base.monthCashFlows(month);
                FiscalYearMonthResponse m = f.size() == 1 ? base.monthSummary(month, f.getFirst(),
                        base.tally(Map.of(month,
                        f.getFirst())))
                        : monthWithoutNumbers(month, f.isEmpty() ? MonthStatus.NO_CASH_FLOW : MonthStatus.TWO_CASH_FLOWS, f);
                months.add(m.withExtended(true));
            }
        }
        if (chosen.isEmpty()) {
            BudgetVsActualStatus s = duplicates.isEmpty() ? BudgetVsActualStatus.NO_CASH_FLOW : BudgetVsActualStatus.TWO_CASH_FLOWS;
            BudgetVsActualResponse r = empty(period, s,
                    duplicates.isEmpty() ? "Nenhum mês do exercício com fluxo carregado"
                    : "Os meses com fluxo têm dois fluxos cada: substitua, reclassifique ou exclua um", budgetBrief,
                            months,
                    base.input)
                    .result();
            return new Calculation(new BudgetVsActualResponse(r.calculationVersion(), r.period(), r.status(),
                    r.message(), r.budget(),
                    r.months(), List.of(), missing.stream().map(YearMonth::toString).toList(), List.copyOf(duplicates),
                            null,
                    false, null, List.of(), null, null, null, null, null, List.of(), r.warnings()), Map.of());
        }
        Tally ap = base.tally(chosen);
        return base.build(period, budgetBrief, ap, chosen.size(), months, summed,
                missing.stream().map(YearMonth::toString).toList(), duplicates, false);
    }

    /** What holds for the whole period: lines, targets, confirmed account mapping and linked funds. */
    private static final class Context {

        final Input input;
        final BudgetStructure structure;
        final Map<UUID, BudgetLine> validTargets = new LinkedHashMap<>();
        final Map<String, MappingTarget> confirmed;
        final Map<String, AccountMapping> mappingByAccount = new HashMap<>();
        final Map<String, ReallocatedEntry> reallocations = new HashMap<>();
        final Map<UUID, BudgetLine> lineByFund = new HashMap<>();
        final List<BudgetLine> fundLines;
        final Map<UUID, BudgetLine> linesById;

        public Context(Input e) {
            this.input = e;
            this.structure = BudgetStructure.of(e.lines());
            AccountMappingService.debitTargets(structure).forEach(l -> validTargets.put(l.getId(), l));
            this.confirmed = EffectiveAccountMapping.confirmed(e.mappings());
            e.mappings().forEach(d -> mappingByAccount.put(d.getAccountCode(), d));
            e.reallocations().forEach(r -> reallocations.put(r.key(), r));
            this.linesById = e.lines().stream().collect(Collectors.toMap(BudgetLine::getId, Function.identity()));
            this.fundLines = structure.funds().map(BudgetStructure.Group::lines).orElse(List.of());
            fundLines.forEach(l -> {
                UUID fund = e.fundByLine().get(l.getId());
                if (fund != null) {
                    lineByFund.put(fund, l);
                }
            });
        }

        public List<CashFlowFile> monthCashFlows(YearMonth month) {
            return input.cashFlows().stream().filter(f -> f.covers(month))
                    .sorted(Comparator.comparing(CashFlowFile::uploadedAt,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(CashFlowFile::fileId))
                    .toList();
        }

        public BigDecimal monthlyPlanned() {
            return structure.monthlyPlannedFromLines().setScale(2, RoundingMode.UNNECESSARY);
        }

        /** Sums the entries of the cash flow chosen for each month (only those of the month itself, by date). */
        public Tally tally(Map<YearMonth, CashFlowFile> chosen) {
            Tally ap = new Tally();
            List<LedgerEntry> sorted = input.entries().stream()
                    .sorted(Comparator.comparing(LedgerEntry::getDate).thenComparing(LedgerEntry::getPage)
                            .thenComparing(LedgerEntry::getSequence).thenComparing(LedgerEntry::getId))
                    .toList();
            for (LedgerEntry l : sorted) {
                CashFlowFile f = chosen.get(YearMonth.from(l.getDate()));
                if (f == null || !f.fileId().equals(l.getFileId())) {
                    continue;
                }
                if (l.getFundId().equals(input.operatingFundId())) {
                    operatingFund(ap, l, f);
                } else {
                    otherFund(ap, l, f);
                }
            }
            return ap;
        }

        private void operatingFund(Tally ap, LedgerEntry l, CashFlowFile f) {
            BigDecimal amount = l.getDebit();
            if (amount.signum() == 0) {
                return;
            }
            ap.debits = ap.debits.add(amount);
            ap.debitCount++;
            String account = l.getAccountCode();
            if (l.isInterFundTransfer()) {
                ap.transfers.add(account, l.getAccountName(), "transferência entre fundos", amount);
                ap.addEvidence(TARGET_TRANSFERS, l, f, name(l.getFundId()), null);
                return;
            }
            if (account != null) {
                ap.accounts.add(account);
            }
            MappingTarget d = account == null ? null : confirmed.get(account);
            if (d == null || (d.type() == MappingTargetType.BUDGET_LINE && !validTargets.containsKey(d.budgetLineId()))) {
                AccountMapping pending = account == null ? null : mappingByAccount.get(account);
                String detail = account == null ? "lançamento sem conta"
                        : pending == null ? "sem de-para"
                        : pending.getStatus() == AccountMappingStatus.CONFIRMED ? "destino inválido"
                        : "de-para " + pending.getStatus().label();
                ap.withoutLine.add(account, l.getAccountName(), detail, amount);
                if (account != null) {
                    ap.accountsWithoutMapping.add(account);
                }
                ap.addEvidence(TARGET_WITHOUT_BUDGET_LINE, l, f, name(l.getFundId()), null);
                return;
            }
            ap.confirmedAccounts.add(account);
            switch (d.type()) {
                case BUDGET_LINE -> ap.line(d.budgetLineId(), amount, l, f, name(l.getFundId()), null, null);
                case ADJUSTMENT -> {
                    ap.adjustments.add(account, l.getAccountName(), d.text(), amount);
                    ap.addEvidence(TARGET_ADJUSTMENTS, l, f, name(l.getFundId()), null);
                }
                case TRANSFER -> {
                    ap.transfers.add(account, l.getAccountName(), d.text(), amount);
                    ap.addEvidence(TARGET_TRANSFERS, l, f, name(l.getFundId()), null);
                }
                case TO_REALLOCATE -> {
                    ReallocatedEntry r = reallocations.get(LedgerEntryFingerprint.key(l));
                    BudgetLine target = r == null ? null : validTargets.get(r.budgetLineId());
                    if (target != null) {
                        ap.usedReallocations.add(r.key());
                        ap.line(target.getId(), amount, l, f, name(l.getFundId()), "realocado para "
                                + target.getEffectiveCode() + " " + target.getDescription() + " por " + r.username()
                                + " em " + DATE.format(r.at().atZone(ZONE)), r.id());
                    } else {
                        ap.toReallocate.add(account, l.getAccountName(), d.text(), amount);
                        ap.addEvidence(TARGET_TO_REALLOCATE, l, f, name(l.getFundId()), null);
                    }
                }
            }
        }

        private void otherFund(Tally ap, LedgerEntry l, CashFlowFile f) {
            FundMovement m = ap.funds.computeIfAbsent(l.getFundId(), k -> new FundMovement());
            m.credits = m.credits.add(l.getCredit());
            m.debits = m.debits.add(l.getDebit());
            if (l.getCredit().signum() != 0 && !l.isInterFundTransfer()) {
                if (l.getCondoFeeReceipt() == null) {
                    m.needsReprocessing = true;
                } else if (l.getCondoFeeReceipt()) {
                    m.collected = m.collected.add(l.getCredit());
                    ap.addEvidence(fundTarget(l.getFundId()), l, f, name(l.getFundId()), null);
                }
            }
        }

        public String name(UUID fundId) {
            return input.fundNames().get(fundId);
        }

        public FiscalYearMonthResponse monthSummary(YearMonth month, CashFlowFile f, Tally ap) {
            BigDecimal planned = monthlyPlanned();
            BigDecimal expense = ap.expense();
            BigDecimal overrun = overrun(ap, 1);
            var rule = input.overrunLimitPercentage() == null ? null
                    : MonthlyOverrunRule.assess(overrun, planned, input.overrunLimitPercentage()).orElse(null);
            return new FiscalYearMonthResponse(month.toString(), MonthStatus.WITH_CASH_FLOW, List.of(f.used()), planned,
                    expense,
                    overrun,
                    rule == null ? null : oneDecimal(rule.percentage()), rule == null ? null : rule.aboveLimit(),
                    false);
        }

        public BigDecimal overrun(Tally ap, int months) {
            BigDecimal sum = ZERO;
            for (BudgetLine l : validTargets.values()) {
                BigDecimal diff = ap.actual(l.getId()).subtract(planned(l, months));
                if (diff.signum() > 0) {
                    sum = sum.add(diff);
                }
            }
            return sum;
        }

        public static BigDecimal planned(BudgetLine l, int months) {
            return l.getBudgeted().multiply(BigDecimal.valueOf(months)).setScale(2, RoundingMode.UNNECESSARY);
        }

        public Calculation build(String period, BudgetBriefResponse budget, Tally ap, int n,
                List<FiscalYearMonthResponse> months, List<String> summed,
                List<String> missing, List<String> duplicates, boolean withRule20) {
            List<BudgetVsActualGroupResponse> groups = new ArrayList<>();
            Map<UUID, List<String>> accountsByLine = new HashMap<>();
            confirmed.forEach((account, d) -> {
                if (d.type() == MappingTargetType.BUDGET_LINE) {
                    accountsByLine.computeIfAbsent(d.budgetLineId(), k -> new ArrayList<>()).add(account);
                }
            });
            BigDecimal totalPlanned = ZERO;
            BigDecimal inLines = ZERO;
            for (BudgetStructure.Group g : structure.groupsWithoutFunds()) {
                List<BudgetVsActualLineResponse> lines = new ArrayList<>();
                BigDecimal gp = ZERO;
                BigDecimal gr = ZERO;
                for (BudgetLine l : g.lines()) {
                    BigDecimal p = planned(l, n);
                    BigDecimal r = ap.actual(l.getId());
                    gp = gp.add(p);
                    gr = gr.add(r);
                    lines.add(new BudgetVsActualLineResponse(l.getId(), l.getEffectiveCode(), l.getDescription(),
                            l.getAccount(),
                            l.getMark(), l.getNotes(), l.getPage(), p, r, r.subtract(p), percentage(r, p),
                            accountsByLine.getOrDefault(l.getId(), List.of()).stream().sorted().toList(),
                            ap.countByLine.getOrDefault(l.getId(), 0)));
                }
                totalPlanned = totalPlanned.add(gp);
                inLines = inLines.add(gr);
                groups.add(new BudgetVsActualGroupResponse(g.line().getId(), g.line().getEffectiveCode(),
                        g.line().getDescription(),
                        gp, gr, gr.subtract(gp), percentage(gr, gp), List.copyOf(lines)));
            }
            BigDecimal expense = ap.expense();
            BigDecimal monthlyPlanned = monthlyPlanned();
            int fiscalYearMonths = months(input.budget().getFiscalYearStart(),
                    input.budget().getFiscalYearEnd()).size();
            BudgetVsActualTotalsResponse totals = new BudgetVsActualTotalsResponse(monthlyPlanned, totalPlanned,
                    expense, inLines, expense.subtract(totalPlanned),
                    percentage(expense, totalPlanned),
                    monthlyPlanned.multiply(BigDecimal.valueOf(fiscalYearMonths)).setScale(2,
                            RoundingMode.UNNECESSARY));
            EntryBlockResponse adjustments = ap.adjustments.block();
            EntryBlockResponse toReallocate = ap.toReallocate.block();
            EntryBlockResponse withoutLine = ap.withoutLine.block();
            BigDecimal transfers = ap.transfers.total;
            CashFlowCheckResponse check = new CashFlowCheckResponse(ap.debits, ap.debitCount, expense,
                    adjustments.total(),
                    transfers, ap.debits.compareTo(expense.add(adjustments.total()).add(transfers)) == 0);
            boolean provisional = toReallocate.entries() > 0 || withoutLine.entries() > 0;

            List<BudgetVsActualWarningResponse> warnings = new ArrayList<>(input.budgetWarnings());
            Rule20Response rule20 = null;
            if (withRule20) {
                rule20 = rule20(ap, n, totalPlanned, toReallocate.total(), withoutLine.total(), provisional, warnings);
            }
            int withoutMapping = ap.accountsWithoutMapping.size();
            if (withoutMapping > 0) {
                warnings.add(new BudgetVsActualWarningResponse("NO_CONFIRMED_MAPPING",
                        withoutMapping + (withoutMapping == 1 ? " conta" : " contas")
                        + " sem de-para confirmado (sem linha da PO): " + MoneyFormatter.format(withoutLine.total())
                        + " fora das linhas da PO"));
            }
            if (withoutLine.accounts().stream().anyMatch(c -> c.account() == null)) {
                warnings.add(new BudgetVsActualWarningResponse("ENTRY_WITHOUT_ACCOUNT",
                        "Lançamentos sem conta do fluxo ficam em \"sem linha da"
                        + " PO\""));
            }
            if (toReallocate.entries() > 0) {
                warnings.add(new BudgetVsActualWarningResponse("TO_REALLOCATE",
                        MoneyFormatter.format(toReallocate.total()) + " a realocar ("
                        + toReallocate.entries() + " lançamentos): fora das linhas da PO até a realocação"));
            }
            ineffectiveReallocations(ap, summed).forEach(warnings::add);
            if (!missing.isEmpty()) {
                warnings.add(new BudgetVsActualWarningResponse("MONTHS_WITHOUT_CASH_FLOW",
                        monthList(missing) + " sem fluxo carregado"));
            }
            duplicates.forEach(m -> warnings.add(new BudgetVsActualWarningResponse("TWO_CASH_FLOWS",
                    mmyyyy(YearMonth.parse(m)) + " com dois fluxos:"
                    + " substitua, reclassifique ou exclua um")));
            List<FundResultResponse> funds = funds(ap, n, warnings);
            if (!check.matches()) {
                warnings.add(new BudgetVsActualWarningResponse("CASH_FLOW_CHECK",
                        "Total de débitos do fundo diferente de despesa + ajustes +"
                        + " transferências"));
            }

            Set<String> accounts = new TreeSet<>(ap.accounts);
            PeriodMappingSummaryResponse mapping = new PeriodMappingSummaryResponse(accounts.size(),
                    (int) accounts.stream().filter(ap.confirmedAccounts::contains).count(), withoutMapping);
            BudgetVsActualResponse r = new BudgetVsActualResponse(VERSION, period, BudgetVsActualStatus.CALCULATED,
                    null, budget,
                    List.copyOf(months),
                    List.copyOf(summed), List.copyOf(missing), List.copyOf(duplicates), mapping, provisional, totals,
                    List.copyOf(groups), adjustments, toReallocate, withoutLine, check, rule20, funds,
                            List.copyOf(warnings));
            Map<String, List<EvidenceResponse>> evidence = new TreeMap<>();
            ap.evidence.forEach((k, v) -> evidence.put(k, List.copyOf(v)));
            return new Calculation(r, evidence);
        }

        /**
         * Reallocations of summed months that went into no line: without a matching entry (the cash flow changed) or
         * with an entry that is no longer in "a realocar". Nothing is summed silently.
         */
        private List<BudgetVsActualWarningResponse> ineffectiveReallocations(Tally ap, List<String> summed) {
            Set<String> periodKeys = input.entries().stream().map(LedgerEntryFingerprint::key)
                    .collect(Collectors.toSet());
            List<BudgetVsActualWarningResponse> warnings = new ArrayList<>();
            input.reallocations().stream().filter(r -> summed.contains(YearMonth.from(r.date()).toString()))
                    .filter(r -> !ap.usedReallocations.contains(r.key()))
                    .sorted(Comparator.comparing(ReallocatedEntry::date).thenComparing(ReallocatedEntry::key))
                    .forEach(r -> {
                        String who = "lançamento de " + DATE.format(r.date()) + (r.account() == null ? ""
                                : ", conta " + r.account()) + ", R$ " + MoneyFormatter.format(r.amount()) + ", página "
                                + r.page();
                        if (!periodKeys.contains(r.key())) {
                            warnings.add(new BudgetVsActualWarningResponse("REALLOCATION_WITHOUT_ENTRY",
                                    "Realocação sem lançamento"
                                    + " correspondente (" + who + "): o fluxo foi lido de novo com outro conteúdo;"
                                    + " o valor não foi somado a nenhuma linha"));
                        } else {
                            warnings.add(new BudgetVsActualWarningResponse("REALLOCATION_WITHOUT_EFFECT",
                                    "Realocação sem efeito (" + who + "): a"
                                    + " conta não está em \"a realocar\" no de-para confirmado ou a linha de destino"
                                    + " não recebe débitos"));
                        }
                    });
            return warnings;
        }

        private Rule20Response rule20(Tally ap, int n, BigDecimal planned, BigDecimal toReallocate,
                BigDecimal withoutLine,
                boolean provisional, List<BudgetVsActualWarningResponse> warnings) {
            if (input.overrunLimitPercentage() == null) {
                warnings.add(new BudgetVsActualWarningResponse("RULE_NOT_EVALUATED",
                        "Regra dos 20% (Conv. 16.2) não avaliada: limite não"
                        + " cadastrado para o condomínio"));
                return null;
            }
            List<OverrunLineResponse> lines = new ArrayList<>();
            BigDecimal overrun = ZERO;
            for (BudgetLine l : validTargets.values()) {
                BigDecimal diff = ap.actual(l.getId()).subtract(planned(l, n));
                if (diff.signum() > 0) {
                    overrun = overrun.add(diff);
                    lines.add(new OverrunLineResponse(l.getId(), l.getEffectiveCode(), l.getDescription(), diff));
                }
            }
            lines.sort(Comparator.comparing(OverrunLineResponse::overrun).reversed().thenComparing(OverrunLineResponse::code));
            var assessment = MonthlyOverrunRule.assess(overrun, planned, input.overrunLimitPercentage()).orElse(null);
            if (assessment == null) {
                warnings.add(new BudgetVsActualWarningResponse("RULE_NOT_EVALUATED",
                        "Regra dos 20% (Conv. 16.2) não avaliada: previsto do mês"
                        + " sem valor"));
                return null;
            }
            BigDecimal scenario = overrun.add(toReallocate).add(withoutLine);
            return new Rule20Response(MonthlyOverrunRule.CODE, MonthlyOverrunRule.VERSION,
                    input.overrunLimitPercentage(),
                    planned,
                    overrun, oneDecimal(assessment.percentage()), assessment.limit(), lines.size(), List.copyOf(lines),
                    toReallocate, withoutLine, scenario, percentage(scenario, planned), provisional,
                            assessment.aboveLimit());
        }

        private List<FundResultResponse> funds(Tally ap, int n, List<BudgetVsActualWarningResponse> warnings) {
            List<FundResultResponse> list = new ArrayList<>();
            for (BudgetLine l : fundLines) {
                UUID fund = input.fundByLine().get(l.getId());
                BigDecimal planned = planned(l, n);
                if (fund == null) {
                    list.add(new FundResultResponse(null, null, l.getId(), l.getEffectiveCode(),
                            FundComparisonStatus.LINE_WITHOUT_FUND, null, null, null, null, null, null));
                    warnings.add(new BudgetVsActualWarningResponse("LINE_WITHOUT_FUND",
                            "linha " + l.getEffectiveCode() + " sem fundo ligado"));
                    continue;
                }
                FundMovement m = ap.funds.getOrDefault(fund, new FundMovement());
                if (m.needsReprocessing) {
                    list.add(new FundResultResponse(fund, name(fund), l.getId(), l.getEffectiveCode(),
                            FundComparisonStatus.REPROCESS_CASH_FLOW, planned, null, null, null, m.credits, m.debits));
                    warnings.add(new BudgetVsActualWarningResponse("REPROCESS_CASH_FLOW",
                            "Fundo " + name(fund) + ": reprocesse o fluxo para"
                            + " apurar a arrecadação (recebimento de cota)"));
                    continue;
                }
                list.add(new FundResultResponse(fund, name(fund), l.getId(), l.getEffectiveCode(),
                        FundComparisonStatus.COMPARED,
                        planned, m.collected, m.collected.subtract(planned), percentage(m.collected, planned),
                        m.credits, m.debits));
            }
            ap.funds.entrySet().stream().filter(x -> !lineByFund.containsKey(x.getKey()))
                    .sorted(Comparator.comparing(x -> Objects.requireNonNullElse(name(x.getKey()), "")))
                    .forEach(x -> list.add(new FundResultResponse(x.getKey(), name(x.getKey()), null, null,
                            FundComparisonStatus.NOT_PLANNED_IN_BUDGET, null, null, null, null, x.getValue().credits,
                            x.getValue().debits)));
            return List.copyOf(list);
        }
    }

    /** Sums of a period (mutable only in here; the result is immutable). */
    private static final class Tally {
        final Map<UUID, BigDecimal> byLine = new HashMap<>();
        final Map<UUID, Integer> countByLine = new HashMap<>();
        final Accumulator adjustments = new Accumulator();
        final Accumulator toReallocate = new Accumulator();
        final Accumulator withoutLine = new Accumulator();
        final Accumulator transfers = new Accumulator();
        final Map<UUID, FundMovement> funds = new LinkedHashMap<>();
        final Set<String> accounts = new TreeSet<>();
        final Set<String> confirmedAccounts = new TreeSet<>();
        final Set<String> accountsWithoutMapping = new TreeSet<>();
        final Map<String, List<EvidenceResponse>> evidence = new LinkedHashMap<>();
        final Set<String> usedReallocations = new TreeSet<>();
        BigDecimal debits = ZERO;
        int debitCount;

        public BigDecimal actual(UUID line) {
            return byLine.getOrDefault(line, ZERO);
        }

        public BigDecimal expense() {
            return byLine.values().stream().reduce(ZERO,
                    BigDecimal::add).add(toReallocate.total).add(withoutLine.total);
        }

        public void line(UUID line, BigDecimal amount, LedgerEntry l, CashFlowFile f, String fund, String reallocation,
                UUID reallocationId) {
            byLine.merge(line, amount, BigDecimal::add);
            countByLine.merge(line, 1, Integer::sum);
            addEvidence(lineTarget(line), l, f, fund, reallocation, reallocationId);
        }

        public void addEvidence(String target, LedgerEntry l, CashFlowFile f, String fund, String reallocation) {
            addEvidence(target, l, f, fund, reallocation, null);
        }

        public void addEvidence(String target, LedgerEntry l, CashFlowFile f, String fund, String reallocation,
                UUID reallocationId) {
            BigDecimal amount = l.getDebit().signum() != 0 ? l.getDebit() : l.getCredit();
            evidence.computeIfAbsent(target, k -> new ArrayList<>()).add(new EvidenceResponse(l.getId(), l.getDate(),
                    l.getAccountCode(), l.getAccountName(), l.getMemo(), l.getSupplier(), l.getDocument(), amount,
                    fund, f.fileId(), f.name(), f.sha256(), l.getPage(), l.getSequence(), reallocation,
                    reallocationId));
        }
    }

    private static final class Accumulator {
        final Map<String,
                BlockAccountResponse> byAccount = new TreeMap<>(Comparator.nullsLast(Comparator.naturalOrder()));
        BigDecimal total = ZERO;
        int entries;

        public void add(String account, String name, String detail, BigDecimal amount) {
            total = total.add(amount);
            entries++;
            byAccount.merge(account, new BlockAccountResponse(account, name, detail, amount, 1), (a,
                    b) -> new BlockAccountResponse(a.account(),
                    a.name() == null ? b.name() : a.name(), a.detail(), a.amount().add(b.amount()),
                    a.entries() + 1));
        }

        public EntryBlockResponse block() {
            return new EntryBlockResponse(total, entries, List.copyOf(byAccount.values()));
        }
    }

    private static final class FundMovement {
        BigDecimal credits = ZERO;
        BigDecimal debits = ZERO;
        BigDecimal collected = ZERO;
        boolean needsReprocessing;
    }

    private static Calculation empty(String period, BudgetVsActualStatus status, String message,
            BudgetBriefResponse budget,
            List<FiscalYearMonthResponse> months, Input e) {
        List<BudgetVsActualWarningResponse> warnings = new ArrayList<>(e.budgetWarnings());
        return new Calculation(new BudgetVsActualResponse(VERSION, period, status, message, budget,
                List.copyOf(months), List.of(),
                List.of(), List.of(), null, false, null, List.of(), null, null, null, null, null, List.of(),
                List.copyOf(warnings)), Map.of());
    }

    /** Extension warning: in the extended month, "PO prorrogada"; in the cumulative, the months left out of the sum. */
    private static BudgetVsActualWarningResponse extensionWarning(Budget budget, BudgetValidity validity,
            BudgetValidity extension,
            Period period, boolean extendedMonth) {
        if (extension == null) {
            return null;
        }
        String until = mmyyyy(extension.end());
        if (extendedMonth && period instanceof Month m) {
            return new BudgetVsActualWarningResponse("BUDGET_EXTENDED",
                    "PO prorrogada: " + mmyyyy(m.month()) + " usa a PO do exercício "
                    + mmyyyy(validity.start()) + " a " + mmyyyy(validity.end()) + ", prorrogada até " + until + " por "
                    + budget.getExtendedBy() + ". Justificativa: " + budget.getExtensionJustification());
        }
        if (period instanceof Cumulative) {
            List<String> extended = months(extension.start(), extension.end()).stream().map(YearMonth::toString)
                    .toList();
            return new BudgetVsActualWarningResponse("EXTENDED_MONTHS",
                    "PO prorrogada até " + until + ": " + monthList(extended)
                    + " aparece" + (extended.size() == 1 ? "" : "m") + " depois do exercício, marcado"
                    + (extended.size() == 1 ? "" : "s") + " \"prorrogado\", e não entra" + (extended.size() == 1
                    ? "" : "m") + " no acumulado.");
        }
        return null;
    }

    private static Input withWarning(Input e, BudgetVsActualWarningResponse warning) {
        if (warning == null) {
            return e;
        }
        List<BudgetVsActualWarningResponse> warnings = new ArrayList<>(e.budgetWarnings());
        warnings.add(warning);
        return new Input(e.budget(), e.budgetFileName(), e.lines(), e.mappings(), e.fundByLine(), e.fundNames(),
                e.operatingFundId(), e.cashFlows(), e.entries(), e.reallocations(), e.overrunLimitPercentage(),
                        warnings,
                e.period());
    }

    private static FiscalYearMonthResponse monthWithoutNumbers(YearMonth month, MonthStatus status,
            List<CashFlowFile> cashFlows) {
        return new FiscalYearMonthResponse(month.toString(), status,
                cashFlows.stream().map(CashFlowFile::used).toList(), null, null, null,
                null, null, false);
    }

    public static List<YearMonth> months(YearMonth start, YearMonth end) {
        List<YearMonth> list = new ArrayList<>();
        for (YearMonth m = start; !m.isAfter(end); m = m.plusMonths(1)) {
            list.add(m);
        }
        return list;
    }

    /** Value ÷ base in %, with 10 decimals and shown with 1 (half up). Zero base: null ("—"). */
    public static BigDecimal percentage(BigDecimal amount, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            return null;
        }
        return oneDecimal(amount.multiply(HUNDRED).divide(base, 10, RoundingMode.HALF_UP));
    }

    public static BigDecimal oneDecimal(BigDecimal v) {
        return v.setScale(1, RoundingMode.HALF_UP);
    }

    public static String mmyyyy(YearMonth m) {
        return "%02d/%d".formatted(m.getMonthValue(), m.getYear());
    }

    /** "mai, jun, jul e ago/2026"; different years: "nov e dez/2026, jan/2027". */
    public static String monthList(List<String> months) {
        Map<Integer, List<String>> byYear = new TreeMap<>();
        months.stream().map(YearMonth::parse).sorted().forEach(m -> byYear.computeIfAbsent(m.getYear(),
                k -> new ArrayList<>()).add(MONTH_NAMES[m.getMonthValue() - 1]));
        return byYear.entrySet().stream().map(x -> join(x.getValue()) + "/" + x.getKey())
                .collect(Collectors.joining(", "));
    }

    private static String join(List<String> names) {
        if (names.size() == 1) {
            return names.getFirst();
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " e " + names.getLast();
    }
}
