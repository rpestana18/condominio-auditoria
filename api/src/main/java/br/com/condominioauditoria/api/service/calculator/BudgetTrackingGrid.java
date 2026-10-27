package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.dto.response.budget.BlockAccountResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualGroupResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualWarningResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetTrackingResponse;
import br.com.condominioauditoria.api.dto.response.budget.EntryBlockResponse;
import br.com.condominioauditoria.api.dto.response.budget.FiscalYearMonthResponse;
import br.com.condominioauditoria.api.dto.response.budget.TrackingBlockCellResponse;
import br.com.condominioauditoria.api.dto.response.budget.TrackingBlockResponse;
import br.com.condominioauditoria.api.dto.response.budget.TrackingBlocksResponse;
import br.com.condominioauditoria.api.dto.response.budget.TrackingCellResponse;
import br.com.condominioauditoria.api.dto.response.budget.TrackingCumulativeResponse;
import br.com.condominioauditoria.api.dto.response.budget.TrackingGroupResponse;
import br.com.condominioauditoria.api.dto.response.budget.TrackingLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.TrackingMonthResponse;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.MappingTarget;
import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import br.com.condominioauditoria.api.model.enums.MappingTargetType;
import br.com.condominioauditoria.api.model.enums.MonthStatus;
import br.com.condominioauditoria.api.model.enums.SpendingProfile;
import br.com.condominioauditoria.api.model.enums.SpendingProfileStatus;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Calculation;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Cumulative;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Month;
import br.com.condominioauditoria.api.util.MoneyFormatter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tracking grid of the budget (RF-13.4 and RF-13.10; ADR 0007, Decision 3). <b>Pure function</b>: calls
 * {@link BudgetVsActualCalculator} for each month of the fiscal year, so each cell has exactly the numbers of the
 * "Previsto × realizado" screen of that month. Then:
 * <ul>
 * <li>cumulative and line balance (planned − actual) only with the <b>complete</b> months that have a cash flow, on
 * both sides (RF-03.1.10); a month without a cash flow has null numbers, never zero;</li>
 * <li>a partial month (cash flow uploaded before the end of its period, RF-13.10) shows its numbers marked "parcial até
 * DD/MM", and stays out of the cumulative, of the line balance and of the 20% rule. While in progress it shows
 * <b>only the actual</b> (Q50): the difference to the planned amount (cells, groups and month total), the overrun and
 * the lines above are null until the month closes; the planned amount of the month stays, as a budget figure;</li>
 * <li>the spending profile never changes the planned amount (Q24); without confirmation the suggestion holds.</li>
 * </ul>
 */
public final class BudgetTrackingGrid {

    /** Version of the grid rules; goes in every result, next to {@link BudgetVsActualCalculator#VERSION}. */
    /** 2: a month in progress shows only the actual, without difference or overrun (Q50). */
    public static final String VERSION = "2";

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    /** Stored spending profile of a line (null profile = nothing stored: the caller passes the suggestion). */
    public record LineProfile(SpendingProfile profile, SpendingProfileStatus status) {

        /**
         * The profile in use (RF-13.14, revised on 26/10/2026): the confirmed one or, without confirmation, the
         * suggestion ("tipo não confirmado"); RECURRING when nothing is known.
         */
        public SpendingProfile effective() {
            return profile != null ? profile : SpendingProfile.RECURRING;
        }
    }

    /**
     * @param base budget vs. actual input with the whole fiscal year's entries (its period is ignored)
     * @param partialUntil partial cash flows: file id → last day with entries
     * @param profiles spending profile by line id
     */
    public record Input(BudgetVsActualCalculator.Input base, Map<UUID, LocalDate> partialUntil,
            Map<UUID, LineProfile> profiles) {

        public Input {
            Objects.requireNonNull(base, "base");
            partialUntil = partialUntil == null ? Map.of() : Map.copyOf(partialUntil);
            profiles = profiles == null ? Map.of() : Map.copyOf(profiles);
        }
    }

    /** One month of the grid: the budget vs. actual calculation and whether it enters the cumulative. */
    public record GridMonth(YearMonth month, Calculation calculation, LocalDate partialUntil) {

        public boolean hasNumbers() {
            return calculation.result().status() == BudgetVsActualStatus.CALCULATED;
        }

        public boolean complete() {
            return hasNumbers() && partialUntil == null;
        }
    }

    public record Grid(BudgetTrackingResponse result, List<GridMonth> months) {
    }

    private BudgetTrackingGrid() {
    }

    public static Grid calculate(Input in) {
        BudgetVsActualCalculator.Input base = in.base();
        // Same status as the cumulative of the budget vs. actual when the grid has no numbers at all
        Calculation cumulative = BudgetVsActualCalculator.calculate(withPeriod(base, new Cumulative()));
        BudgetVsActualResponse c = cumulative.result();
        if (c.status() != BudgetVsActualStatus.CALCULATED && c.status() != BudgetVsActualStatus.NO_CASH_FLOW
                && c.status() != BudgetVsActualStatus.TWO_CASH_FLOWS) {
            return new Grid(new BudgetTrackingResponse(VERSION, BudgetVsActualCalculator.VERSION, c.status(),
                    c.message(), c.budget(), List.of(), List.of(), List.of(), null, null, c.warnings()), List.of());
        }
        BudgetValidity validity = BudgetValidity.of(base.budget()).orElseThrow();
        List<GridMonth> months = new ArrayList<>();
        for (YearMonth m : BudgetVsActualCalculator.months(validity.start(), validity.end())) {
            Calculation calc = BudgetVsActualCalculator.calculate(withPeriod(base, new Month(m)));
            LocalDate partial = null;
            if (calc.result().status() == BudgetVsActualStatus.CALCULATED) {
                UUID file = calc.result().months().getFirst().cashFlows().getFirst().fileId();
                partial = in.partialUntil().get(file);
            }
            months.add(new GridMonth(m, calc, partial));
        }

        BudgetStructure structure = BudgetStructure.of(base.lines());
        Map<String, MappingTarget> confirmed = EffectiveAccountMapping.confirmed(base.mappings());
        Map<UUID, List<String>> accountsByLine = new HashMap<>();
        confirmed.forEach((account, t) -> {
            if (t.type() == MappingTargetType.BUDGET_LINE) {
                accountsByLine.computeIfAbsent(t.budgetLineId(), k -> new ArrayList<>()).add(account);
            }
        });
        List<Map<UUID, BudgetVsActualLineResponse>> lineByMonth = new ArrayList<>();
        List<Map<UUID, BudgetVsActualGroupResponse>> groupByMonth = new ArrayList<>();
        for (GridMonth gm : months) {
            Map<UUID, BudgetVsActualLineResponse> lines = new HashMap<>();
            Map<UUID, BudgetVsActualGroupResponse> groups = new HashMap<>();
            if (gm.hasNumbers()) {
                for (BudgetVsActualGroupResponse g : gm.calculation().result().groups()) {
                    groups.put(g.lineId(), g);
                    g.lines().forEach(l -> lines.put(l.lineId(), l));
                }
            }
            lineByMonth.add(lines);
            groupByMonth.add(groups);
        }

        List<TrackingGroupResponse> groups = new ArrayList<>();
        BigDecimal separateCumulative = ZERO;
        List<BigDecimal> separateByMonth = new ArrayList<>();
        List<Integer> separateEntries = new ArrayList<>();
        months.forEach(m -> {
            separateByMonth.add(m.hasNumbers() ? ZERO : null);
            separateEntries.add(m.hasNumbers() ? 0 : null);
        });
        int separateCumulativeEntries = 0;
        for (BudgetStructure.Group g : structure.groupsWithoutFunds()) {
            List<TrackingLineResponse> lines = new ArrayList<>();
            for (BudgetLine l : g.lines()) {
                LineProfile p = in.profiles().getOrDefault(l.getId(), new LineProfile(null, null));
                SpendingProfile effective = p.effective();
                List<TrackingCellResponse> cells = new ArrayList<>();
                BigDecimal planned = ZERO;
                BigDecimal actual = ZERO;
                int above = 0;
                for (int i = 0; i < months.size(); i++) {
                    GridMonth gm = months.get(i);
                    BudgetVsActualLineResponse r = lineByMonth.get(i).get(l.getId());
                    if (r == null) {
                        cells.add(new TrackingCellResponse(gm.month().toString(), null, null, null, null));
                        continue;
                    }
                    cells.add(new TrackingCellResponse(gm.month().toString(), r.planned(), r.actual(),
                            gm.complete() ? r.difference() : null, r.entries()));
                    if (effective == SpendingProfile.SEPARATE_APPORTIONMENT) {
                        separateByMonth.set(i, separateByMonth.get(i).add(r.actual()));
                        separateEntries.set(i, separateEntries.get(i) + r.entries());
                    }
                    if (gm.complete()) {
                        planned = planned.add(r.planned());
                        actual = actual.add(r.actual());
                        if (r.difference().signum() > 0) {
                            above++;
                        }
                        if (effective == SpendingProfile.SEPARATE_APPORTIONMENT) {
                            separateCumulative = separateCumulative.add(r.actual());
                            separateCumulativeEntries += r.entries();
                        }
                    }
                }
                lines.add(new TrackingLineResponse(l.getId(), l.getPosition(), l.getEffectiveCode(),
                        l.getPrintedCode(), l.getDescription(), l.getAccount(), l.getMark(), l.getNotes(), l.getPage(),
                        p.profile(), p.status(), effective, l.getBudgeted(),
                        accountsByLine.getOrDefault(l.getId(), List.of()).stream().sorted().toList(),
                        List.copyOf(cells), planned, actual, planned.subtract(actual), above));
            }
            List<TrackingCellResponse> cells = new ArrayList<>();
            BigDecimal planned = ZERO;
            BigDecimal actual = ZERO;
            for (int i = 0; i < months.size(); i++) {
                GridMonth gm = months.get(i);
                BudgetVsActualGroupResponse r = groupByMonth.get(i).get(g.line().getId());
                if (r == null) {
                    cells.add(new TrackingCellResponse(gm.month().toString(), null, null, null, null));
                    continue;
                }
                int entries = r.lines().stream().mapToInt(BudgetVsActualLineResponse::entries).sum();
                cells.add(new TrackingCellResponse(gm.month().toString(), r.planned(), r.actual(),
                        gm.complete() ? r.difference() : null, entries));
                if (gm.complete()) {
                    planned = planned.add(r.planned());
                    actual = actual.add(r.actual());
                }
            }
            groups.add(new TrackingGroupResponse(g.line().getId(), g.line().getEffectiveCode(),
                    g.line().getDescription(), List.copyOf(cells), planned, actual, planned.subtract(actual),
                    List.copyOf(lines)));
        }

        List<TrackingMonthResponse> monthResponses = new ArrayList<>();
        List<String> cumulativeMonths = new ArrayList<>();
        BigDecimal planned = ZERO;
        BigDecimal inLines = ZERO;
        BigDecimal expense = ZERO;
        List<String> partialMonths = new ArrayList<>();
        for (GridMonth gm : months) {
            monthResponses.add(month(gm));
            if (gm.complete()) {
                BudgetVsActualResponse r = gm.calculation().result();
                cumulativeMonths.add(gm.month().toString());
                planned = planned.add(r.totals().planned());
                inLines = inLines.add(r.totals().inLines());
                expense = expense.add(r.totals().actualExpense());
            } else if (gm.hasNumbers()) {
                partialMonths.add(BudgetVsActualCalculator.mmyyyy(gm.month()) + " parcial até "
                        + DAY_MONTH.format(gm.partialUntil()));
            }
        }
        TrackingBlocksResponse blocks = new TrackingBlocksResponse(
                block(months, BudgetVsActualResponse::adjustments),
                block(months, BudgetVsActualResponse::toReallocate),
                block(months, BudgetVsActualResponse::withoutBudgetLine),
                separate(months, separateByMonth, separateEntries, separateCumulative, separateCumulativeEntries));

        List<BudgetVsActualWarningResponse> warnings = new ArrayList<>(base.budgetWarnings());
        YearMonth last = months.stream().filter(GridMonth::hasNumbers).map(GridMonth::month).reduce((a, b) -> b)
                .orElse(null);
        List<String> missing = months.stream().filter(m -> !m.hasNumbers() && last != null
                && m.month().isBefore(last) && m.calculation().result().status() == BudgetVsActualStatus.NO_CASH_FLOW)
                .map(m -> m.month().toString()).toList();
        if (!missing.isEmpty()) {
            warnings.add(new BudgetVsActualWarningResponse("MONTHS_WITHOUT_CASH_FLOW",
                    BudgetVsActualCalculator.monthList(missing) + " sem extrato carregado"));
        }
        unmappedWarning(unmapped(months.stream().filter(GridMonth::complete)
                .map(m -> m.calculation().result().withoutBudgetLine()).toList()),
                " no acumulado", "o previsto das linhas dessas despesas fica como saldo da linha")
                .ifPresent(warnings::add);
        partialMonths.forEach(t -> warnings.add(new BudgetVsActualWarningResponse("PARTIAL_MONTH", t
                + ": aparece com os números até a data, fora do acumulado, do saldo das linhas e da regra dos 20%")));
        months.stream().filter(m -> m.calculation().result().status() == BudgetVsActualStatus.TWO_CASH_FLOWS)
                .forEach(m -> warnings.add(new BudgetVsActualWarningResponse("TWO_CASH_FLOWS",
                        BudgetVsActualCalculator.mmyyyy(m.month()) + " com dois fluxos: substitua, reclassifique ou"
                                + " exclua um")));
        long unconfirmed = groups.stream().flatMap(g -> g.lines().stream())
                .filter(l -> l.spendingProfileStatus() != SpendingProfileStatus.CONFIRMED).count();
        if (unconfirmed > 0) {
            warnings.add(new BudgetVsActualWarningResponse("PROFILE_NOT_CONFIRMED", unconfirmed
                    + (unconfirmed == 1 ? " linha com tipo não confirmado" : " linhas com tipo não confirmado")
                    + ": vale a sugestão até o Admin confirmar"));
        }
        BudgetTrackingResponse result = new BudgetTrackingResponse(VERSION, BudgetVsActualCalculator.VERSION,
                cumulativeMonths.isEmpty() && partialMonths.isEmpty() ? c.status() : BudgetVsActualStatus.CALCULATED,
                cumulativeMonths.isEmpty() && partialMonths.isEmpty() ? c.message() : null, c.budget(),
                List.copyOf(monthResponses), List.copyOf(cumulativeMonths), List.copyOf(groups),
                new TrackingCumulativeResponse(cumulativeMonths.size(), planned, inLines, expense,
                        planned.subtract(inLines)),
                blocks, List.copyOf(warnings));
        return new Grid(result, List.copyOf(months));
    }

    private static TrackingMonthResponse month(GridMonth gm) {
        BudgetVsActualResponse r = gm.calculation().result();
        FiscalYearMonthResponse m = r.months().isEmpty() ? null : r.months().getFirst();
        MonthStatus status = m == null ? MonthStatus.NO_CASH_FLOW : m.status();
        if (!gm.hasNumbers()) {
            return new TrackingMonthResponse(gm.month().toString(), status, null, false,
                    m == null ? List.of() : m.cashFlows(), null, null, null, null, null, null, null, null);
        }
        boolean complete = gm.complete();
        var rule = r.rule20();
        // A month in progress: only the actual (Q50)
        return new TrackingMonthResponse(gm.month().toString(), status, gm.partialUntil(), complete, m.cashFlows(),
                r.totals().planned(), r.totals().inLines(), r.totals().actualExpense(),
                complete ? r.totals().inLines().subtract(r.totals().planned()) : null, complete ? m.overrun() : null,
                complete && rule != null ? rule.linesAbove() : null,
                complete && rule != null ? rule.percentage() : null, complete && rule != null ? rule.aboveLimit() : null);
    }

    private static TrackingBlockResponse block(List<GridMonth> months,
            Function<BudgetVsActualResponse, EntryBlockResponse> of) {
        List<TrackingBlockCellResponse> cells = new ArrayList<>();
        List<EntryBlockResponse> complete = new ArrayList<>();
        BigDecimal cumulative = ZERO;
        int entries = 0;
        for (GridMonth gm : months) {
            if (!gm.hasNumbers()) {
                cells.add(new TrackingBlockCellResponse(gm.month().toString(), null, null));
                continue;
            }
            EntryBlockResponse b = of.apply(gm.calculation().result());
            cells.add(new TrackingBlockCellResponse(gm.month().toString(), b.total(), b.entries()));
            if (gm.complete()) {
                cumulative = cumulative.add(b.total());
                entries += b.entries();
                complete.add(b);
            }
        }
        return new TrackingBlockResponse(List.copyOf(cells), cumulative, entries, merge(complete));
    }

    private static TrackingBlockResponse separate(List<GridMonth> months, List<BigDecimal> amounts,
            List<Integer> entries, BigDecimal cumulative, int cumulativeEntries) {
        List<TrackingBlockCellResponse> cells = new ArrayList<>();
        for (int i = 0; i < months.size(); i++) {
            cells.add(new TrackingBlockCellResponse(months.get(i).month().toString(), amounts.get(i), entries.get(i)));
        }
        return new TrackingBlockResponse(List.copyOf(cells), cumulative, cumulativeEntries, List.of());
    }

    /**
     * Accounts of the operating fund without a confirmed mapping (the "sem linha da PO" block of the budget vs. actual),
     * merged over some months. {@code accounts}: largest amount first, the entries without an account last (account
     * null). {@code count}: accounts with a code, as in the NO_CONFIRMED_MAPPING warning of the budget vs. actual.
     */
    public record UnmappedAccounts(List<BlockAccountResponse> accounts, BigDecimal total, int entries) {

        public long count() {
            return accounts.stream().filter(a -> a.account() != null).count();
        }
    }

    /** The "sem linha da PO" block of each month with numbers (complete or partial), for the balance bridge. */
    public static Map<YearMonth, EntryBlockResponse> withoutBudgetLineByMonth(List<GridMonth> months) {
        Map<YearMonth, EntryBlockResponse> byMonth = new LinkedHashMap<>();
        months.stream().filter(GridMonth::hasNumbers)
                .forEach(m -> byMonth.put(m.month(), m.calculation().result().withoutBudgetLine()));
        return byMonth;
    }

    /** Merges "sem linha da PO" blocks of several months. */
    public static UnmappedAccounts unmapped(List<EntryBlockResponse> blocks) {
        BigDecimal total = ZERO;
        int entries = 0;
        for (EntryBlockResponse b : blocks) {
            total = total.add(b.total());
            entries += b.entries();
        }
        return new UnmappedAccounts(merge(blocks), total, entries);
    }

    /**
     * Warning NO_CONFIRMED_MAPPING (RF-03.1.10) with the accounts and the amount, as in the budget vs. actual.
     * {@code where}: the period of the amount (" no acumulado"); {@code effect}: what it means on that screen.
     */
    public static Optional<BudgetVsActualWarningResponse> unmappedWarning(UnmappedAccounts u, String where,
            String effect) {
        long count = u.count();
        if (count == 0) {
            return Optional.empty();
        }
        String accounts = u.accounts().stream()
                .map(a -> (a.account() == null ? "lançamentos sem conta"
                        : a.account() + (a.name() == null || a.name().isBlank() ? "" : " " + a.name()))
                        + " R$ " + MoneyFormatter.format(a.amount()))
                .collect(Collectors.joining("; "));
        return Optional.of(new BudgetVsActualWarningResponse("NO_CONFIRMED_MAPPING", count
                + (count == 1 ? " conta" : " contas") + " sem de-para confirmado" + where + ": R$ "
                + MoneyFormatter.format(u.total()) + " em \"sem linha da PO\", fora das linhas da PO; " + effect
                + ". Contas: " + accounts));
    }

    /** Merges the accounts of several blocks by account code: amounts and entries added, largest amount first. */
    static List<BlockAccountResponse> merge(List<EntryBlockResponse> blocks) {
        Map<String, BlockAccountResponse> byAccount = new LinkedHashMap<>();
        for (EntryBlockResponse b : blocks) {
            for (BlockAccountResponse a : b.accounts()) {
                byAccount.merge(Objects.requireNonNullElse(a.account(), ""), a,
                        (x, y) -> new BlockAccountResponse(x.account(), x.name() == null ? y.name() : x.name(),
                                x.detail() == null ? y.detail() : x.detail(), x.amount().add(y.amount()),
                                x.entries() + y.entries()));
            }
        }
        return byAccount.values().stream()
                .sorted(Comparator.comparing((BlockAccountResponse a) -> a.account() == null)
                        .thenComparing(BlockAccountResponse::amount, Comparator.reverseOrder())
                        .thenComparing(a -> Objects.requireNonNullElse(a.account(), "")))
                .toList();
    }

    /** Accounts of a block, merged over the complete months (for the bridge parts). */
    public static Map<String, BigDecimal> blockAccounts(List<GridMonth> months,
            Function<BudgetVsActualResponse, EntryBlockResponse> of) {
        Map<String, BigDecimal> byAccount = new LinkedHashMap<>();
        for (GridMonth gm : months) {
            if (gm.complete()) {
                for (BlockAccountResponse a : of.apply(gm.calculation().result()).accounts()) {
                    byAccount.merge(Objects.requireNonNullElse(a.account(), "sem conta"), a.amount(), BigDecimal::add);
                }
            }
        }
        return byAccount;
    }

    /** Copy of the budget vs. actual input with another period. */
    static BudgetVsActualCalculator.Input withPeriod(BudgetVsActualCalculator.Input e,
            BudgetVsActualCalculator.Period period) {
        return new BudgetVsActualCalculator.Input(e.budget(), e.budgetFileName(), e.lines(), e.mappings(),
                e.fundByLine(), e.fundNames(), e.operatingFundId(), e.cashFlows(), e.entries(), e.reallocations(),
                e.overrunLimitPercentage(), e.budgetWarnings(), period, e.partialUntil());
    }
}
