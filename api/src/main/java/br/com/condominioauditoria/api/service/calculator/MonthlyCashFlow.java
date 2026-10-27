package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.dto.response.accounting.CashFlowEntryResponse;
import br.com.condominioauditoria.api.dto.response.accounting.CashFlowGroupResponse;
import br.com.condominioauditoria.api.dto.response.accounting.CashFlowMonthResponse;
import br.com.condominioauditoria.api.dto.response.accounting.CashFlowMonthlyResponse;
import br.com.condominioauditoria.api.dto.response.accounting.CashFlowRowResponse;
import br.com.condominioauditoria.api.dto.response.accounting.FundMonthMovementResponse;
import br.com.condominioauditoria.api.dto.response.accounting.FundMonthSummaryResponse;
import br.com.condominioauditoria.api.dto.response.accounting.FundMovementResponse;
import br.com.condominioauditoria.api.dto.response.accounting.FundMovementsResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetBriefResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualGroupResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualWarningResponse;
import br.com.condominioauditoria.api.dto.response.budget.EvidenceResponse;
import br.com.condominioauditoria.api.dto.response.budget.LineReviewStateResponse;
import br.com.condominioauditoria.api.dto.response.budget.UsedCashFlowResponse;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.enums.BudgetLineMark;
import br.com.condominioauditoria.api.model.enums.CashFlowRowCode;
import br.com.condominioauditoria.api.model.enums.MonthStatus;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Calculation;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * "Entradas e saídas" month by month (RF-13.11 and RF-13.16; ADR 0009, Decision 4). <b>Pure function</b> over data that
 * already exists: the fund movements of each month ({@link FundMovements}, the printed balances and the marks of the
 * entries), the budget vs. actual of each month of the budget ({@link BudgetVsActualCalculator}, the destination of
 * each operating fund entry by the account mapping) and the entries of the cash flow used in each month. Nothing is
 * stored.
 *
 * <ul>
 * <li>Inflows: fee received, other income and the other credits of the fund (in the view of all funds, the inflows of
 * the other funds in a row of their own, fund by fund). Transfers between funds apart.</li>
 * <li>Outflows of the operating fund, in the months of the budget and with the lines reviewed (RF-13.12): by budget line
 * (groups 1.1 to 1.8, every line, also the zero ones), then the blocks "rateio à parte (concessionárias)", "sem linha da
 * PO", "a realocar" and "ajustes". Before the budget (RF-13.16) or with the lines not reviewed: by cash flow account.
 * Outflows of the other funds: "pago por outros fundos" fund by fund (view of all funds) or by account (one fund).</li>
 * <li>Each month: monthBalance = inflows − outflows + transfers in − transfers out, the printed closing balance and the
 * check against the cash flow (D6): outflows + transfers out = debits of the cash flow to the cent; otherwise the
 * warning "saídas não batem com o extrato" with both amounts.</li>
 * <li>Month without a cash flow: every number null ("sem extrato carregado", never zero, RF-03.1.10).</li>
 * </ul>
 * The same classification of each entry gives the entries list ({@link #entries}), so a cell and its list always add up.
 */
public final class MonthlyCashFlow {

    public static final String VERSION = "1";

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    private MonthlyCashFlow() {
    }

    /**
     * @param months months of the table, in order
     * @param funds fund movements of the same months
     * @param calculations budget vs. actual of each month of the budget with status CALCULATED (operating fund)
     * @param budgetMonths months with an approved budget (the others are "antes da PO aprovada")
     * @param entries entries of the cash flows of the months (the ones not used in a month are ignored)
     * @param fundId the fund of the table; ignored with {@code allFunds}
     * @param review review state of the budget lines; null without a budget
     */
    public record Input(List<YearMonth> months, FundMovementsResponse funds, Map<YearMonth, Calculation> calculations,
            Set<YearMonth> budgetMonths, List<LedgerEntry> entries, UUID fundId, boolean allFunds, UUID operatingFundId,
            LineReviewStateResponse review, BudgetBriefResponse budget) {

        public Input {
            months = List.copyOf(months);
            Objects.requireNonNull(funds, "funds");
            calculations = calculations == null ? Map.of() : Map.copyOf(calculations);
            budgetMonths = budgetMonths == null ? Set.of() : Set.copyOf(budgetMonths);
            entries = entries == null ? List.of() : List.copyOf(entries);
        }

        boolean reviewed() {
            return review != null && review.reviewed();
        }
    }

    /** Destination of an entry: the row it goes to, with the budget line when it is one. */
    public record Destination(CashFlowRowCode code, UUID lineId) {
    }

    /** Filters of the entries list; null = any. {@code block} BUDGET_LINE = any budget line. */
    public record EntryFilter(YearMonth month, UUID lineId, CashFlowRowCode block, String account) {
    }

    public static CashFlowMonthlyResponse calculate(Input in) {
        Context ctx = new Context(in);
        List<YearMonth> months = in.months();
        Rows inflows = new Rows();
        Rows blocks = new Rows();
        Rows transfers = new Rows();
        Map<UUID, Row> lineRows = new LinkedHashMap<>();
        Map<UUID, GroupInfo> groups = new LinkedHashMap<>();
        List<CashFlowMonthResponse> monthResponses = new ArrayList<>();
        List<BudgetVsActualWarningResponse> warnings = new ArrayList<>();
        if (in.review() != null && !in.reviewed()) {
            warnings.add(new BudgetVsActualWarningResponse("LINES_NOT_REVIEWED", in.review().message()));
        }
        // Rows always shown, in this order
        inflows.get(CashFlowRowCode.CONDO_FEE, "Cota recebida");
        inflows.get(CashFlowRowCode.OTHER_INCOME, "Outras receitas");
        inflows.get(CashFlowRowCode.OTHER_CREDITS, "Cotas extras e outros créditos");
        if (in.allFunds()) {
            inflows.get(CashFlowRowCode.OTHER_FUNDS_INCOME, "Entradas dos outros fundos");
        }
        boolean anyByLine = months.stream().anyMatch(ctx::byLine);
        if (anyByLine) {
            blocks.get(CashFlowRowCode.SEPARATE_APPORTIONMENT, "Rateio à parte (concessionárias)");
            blocks.get(CashFlowRowCode.WITHOUT_BUDGET_LINE, "Sem linha da PO");
            blocks.get(CashFlowRowCode.TO_REALLOCATE, "A realocar");
            blocks.get(CashFlowRowCode.ADJUSTMENTS, "Ajustes");
        }
        if (months.stream().anyMatch(ctx::byAccount)) {
            blocks.get(CashFlowRowCode.BY_ACCOUNT, "Saídas por conta do extrato");
        }
        if (in.allFunds()) {
            blocks.get(CashFlowRowCode.OTHER_FUNDS, "Pago por outros fundos");
        }
        transfers.get(CashFlowRowCode.TRANSFERS_IN, "Transferências recebidas de outros fundos");
        transfers.get(CashFlowRowCode.TRANSFERS_OUT, "Transferências enviadas a outros fundos");

        for (YearMonth m : months) {
            FundMonthSummaryResponse summary = ctx.summary(m);
            MonthStatus status = summary == null ? MonthStatus.NO_CASH_FLOW : summary.status();
            LocalDate partialUntil = summary == null ? null : summary.partialUntil();
            boolean beforeBudget = !in.budgetMonths().contains(m);
            if (!ctx.hasNumbers(m)) {
                monthResponses.add(new CashFlowMonthResponse(m.toString(), status, partialUntil, beforeBudget, false,
                        null, null, null, null, null, null, null, null, null, null, null));
                continue;
            }
            Calculation calc = ctx.calculation(m);
            if (ctx.byLine(m)) {
                registerLines(calc, lineRows, groups);
            }
            BigDecimal tin = ZERO;
            BigDecimal tout = ZERO;
            BigDecimal opening = ZERO;
            BigDecimal closing = ZERO;
            BigDecimal printedDebits = ZERO;
            BigDecimal entryDebits = ZERO;
            boolean openingKnown = true;
            boolean closingKnown = true;
            boolean printedKnown = true;
            Boolean balanceMatches = null;
            for (FundMovementResponse fund : ctx.selectedFunds()) {
                FundMonthMovementResponse mv = ctx.movement(fund, m);
                if (mv == null || !mv.present()) {
                    continue;
                }
                boolean operating = fund.fundId().equals(in.operatingFundId());
                if (operating || !in.allFunds()) {
                    inflows.get(CashFlowRowCode.CONDO_FEE, null).add(m, mv.condoFee());
                    inflows.get(CashFlowRowCode.OTHER_INCOME, null).add(m, mv.otherIncome());
                    inflows.get(CashFlowRowCode.OTHER_CREDITS, null).add(m, mv.otherCredits());
                } else {
                    BigDecimal income = mv.condoFee().add(mv.otherIncome()).add(mv.otherCredits());
                    Row r = inflows.get(CashFlowRowCode.OTHER_FUNDS_INCOME, null);
                    r.add(m, income);
                    r.child("fund:" + fund.fundId(), () -> fundRow(CashFlowRowCode.OTHER_FUNDS_INCOME, fund))
                            .add(m, income);
                }
                if (!operating && in.allFunds()) {
                    BigDecimal spent = mv.debits().subtract(mv.transfersOut());
                    Row r = blocks.get(CashFlowRowCode.OTHER_FUNDS, null);
                    r.add(m, spent);
                    r.child("fund:" + fund.fundId(), () -> fundRow(CashFlowRowCode.OTHER_FUNDS, fund)).add(m, spent);
                } else {
                    for (LedgerEntry e : ctx.entriesOf(fund.fundId(), m)) {
                        if (e.getDebit().signum() == 0) {
                            continue;
                        }
                        Destination d = ctx.destination(e, m);
                        addOutflow(d, e, m, lineRows, blocks);
                    }
                }
                tin = tin.add(mv.transfersIn());
                tout = tout.add(mv.transfersOut());
                entryDebits = entryDebits.add(mv.debits());
                if (mv.openingBalance() == null) {
                    openingKnown = false;
                } else {
                    opening = opening.add(mv.openingBalance());
                }
                if (mv.closingBalance() == null) {
                    closingKnown = false;
                } else {
                    closing = closing.add(mv.closingBalance());
                }
                if (mv.printedDebits() == null) {
                    printedKnown = false;
                } else {
                    printedDebits = printedDebits.add(mv.printedDebits());
                }
                if (mv.balanceMatches() != null) {
                    balanceMatches = (balanceMatches == null || balanceMatches) && mv.balanceMatches();
                }
            }
            transfers.get(CashFlowRowCode.TRANSFERS_IN, null).add(m, tin);
            transfers.get(CashFlowRowCode.TRANSFERS_OUT, null).add(m, tout);
            BigDecimal in_ = inflows.sum(m);
            BigDecimal inLines = ctx.byLine(m) ? lineRows.values().stream().map(r -> r.value(m))
                    .reduce(ZERO, BigDecimal::add).add(blocks.value(CashFlowRowCode.SEPARATE_APPORTIONMENT, m)) : null;
            BigDecimal out = blocks.sum(m).add(ctx.byLine(m) ? lineRows.values().stream().map(r -> r.value(m))
                    .reduce(ZERO, BigDecimal::add) : ZERO);
            BigDecimal reference = printedKnown ? printedDebits : entryDebits;
            boolean debitsMatch = out.add(tout).compareTo(reference) == 0;
            if (!debitsMatch) {
                warnings.add(new BudgetVsActualWarningResponse("DEBITS_DO_NOT_MATCH", "Saídas não batem com o extrato"
                        + " em " + BudgetVsActualCalculator.mmyyyy(m) + ": saídas R$ " + MoneyFormatter.format(out)
                        + " + transferências enviadas R$ " + MoneyFormatter.format(tout) + "; débitos do extrato R$ "
                        + MoneyFormatter.format(reference)));
            }
            if (Boolean.FALSE.equals(balanceMatches)) {
                warnings.add(new BudgetVsActualWarningResponse("BALANCE_DOES_NOT_MATCH", "Saldo acumulado de "
                        + BudgetVsActualCalculator.mmyyyy(m) + " não bate com o saldo impresso no extrato"));
            }
            if (partialUntil != null) {
                warnings.add(new BudgetVsActualWarningResponse("PARTIAL_MONTH", "Mês em andamento · parcial até "
                        + DAY_MONTH.format(partialUntil) + " (" + BudgetVsActualCalculator.mmyyyy(m) + ")"));
            }
            monthResponses.add(new CashFlowMonthResponse(m.toString(), status, partialUntil, beforeBudget,
                    ctx.byAccount(m), in_, out, inLines, tin, tout, in_.subtract(out).add(tin).subtract(tout),
                    openingKnown ? opening : null, closingKnown ? closing : null, printedKnown ? printedDebits : null,
                    debitsMatch, balanceMatches));
        }
        in.funds().warnings().stream()
                .filter(w -> w.code().equals("TWO_CASH_FLOWS") || w.code().equals("CASH_FLOW_SPANS_MONTHS"))
                .forEach(warnings::add);

        // Rows to responses: inflows and transfers where the month has numbers; lines and blocks where they apply
        List<CashFlowRowResponse> inflowRows = inflows.responses(months, ctx::hasNumbers);
        List<CashFlowGroupResponse> groupRows = new ArrayList<>();
        for (GroupInfo g : groups.values()) {
            if (g.lines.isEmpty()) {
                // A group with only separate apportionment lines (1.4 in the pilot): they are in their own block
                continue;
            }
            List<CashFlowRowResponse> lines = g.lines.stream().map(id -> lineRows.get(id).response(months, ctx::byLine))
                    .toList();
            List<BigDecimal> cells = sumCells(months, lines, ctx::byLine);
            groupRows.add(new CashFlowGroupResponse(g.lineId, g.code, g.description, cells, total(cells), lines));
        }
        List<CashFlowRowResponse> blockRows = new ArrayList<>();
        for (Row r : blocks.rows.values()) {
            java.util.function.Predicate<YearMonth> applies = switch (r.code) {
                case BY_ACCOUNT -> ctx::byAccount;
                case OTHER_FUNDS -> ctx::hasNumbers;
                default -> ctx::byLine;
            };
            blockRows.add(r.response(months, applies));
        }
        List<CashFlowRowResponse> transferRows = transfers.responses(months, ctx::hasNumbers);
        FundMovementResponse fund = in.allFunds() ? null : ctx.fund(in.fundId());
        return new CashFlowMonthlyResponse(VERSION, in.allFunds() ? null : in.fundId(),
                fund == null ? null : fund.name(), in.allFunds(), months.isEmpty() ? null : months.getFirst().toString(),
                months.isEmpty() ? null : months.getLast().toString(), in.budget(), in.review(),
                List.copyOf(monthResponses), inflowRows, List.copyOf(groupRows), List.copyOf(blockRows), transferRows,
                List.copyOf(warnings));
    }

    /** Entries of the table with their destination, in date order, after the filters. */
    public static List<CashFlowEntryResponse> entries(Input in, EntryFilter filter) {
        Context ctx = new Context(in);
        Map<UUID, BudgetVsActualLineResponse> lines = new HashMap<>();
        in.calculations().values().forEach(c -> c.result().groups().forEach(g -> g.lines()
                .forEach(l -> lines.putIfAbsent(l.lineId(), l))));
        List<CashFlowEntryResponse> list = new ArrayList<>();
        for (YearMonth m : in.months()) {
            if ((filter.month() != null && !filter.month().equals(m)) || !ctx.hasNumbers(m)) {
                continue;
            }
            UsedCashFlowResponse file = ctx.file(m);
            for (FundMovementResponse fund : ctx.selectedFunds()) {
                FundMonthMovementResponse mv = ctx.movement(fund, m);
                if (mv == null || !mv.present()) {
                    continue;
                }
                for (LedgerEntry e : ctx.entriesOf(fund.fundId(), m)) {
                    boolean debit = e.getDebit().signum() != 0;
                    Destination d = debit ? ctx.destination(e, m) : ctx.creditDestination(e, fund);
                    if (filter.lineId() != null && !filter.lineId().equals(d.lineId())) {
                        continue;
                    }
                    if (filter.block() != null && filter.block() != d.code()
                            && !(filter.block() == CashFlowRowCode.BUDGET_LINE && d.lineId() != null)) {
                        continue;
                    }
                    if (filter.account() != null && !filter.account().equals(e.getAccountCode())) {
                        continue;
                    }
                    BudgetVsActualLineResponse line = d.lineId() == null ? null : lines.get(d.lineId());
                    list.add(new CashFlowEntryResponse(e.getId(), e.getDate(), m.toString(), e.getMemo(),
                            e.getSupplier(), e.getDocument(), e.getAccountCode(), e.getAccountName(), fund.fundId(),
                            fund.name(), debit ? e.getDebit() : e.getCredit(), debit ? "OUT" : "IN", d.code(),
                            d.lineId(), line == null ? null : line.code(), line == null ? null : line.description(),
                            label(d, line, fund), file == null ? null : file.fileId(),
                            file == null ? null : file.name(), file == null ? null : file.sha256(), e.getPage(),
                            e.getSequence()));
                }
            }
        }
        return List.copyOf(list);
    }

    private static String label(Destination d, BudgetVsActualLineResponse line, FundMovementResponse fund) {
        return switch (d.code()) {
            case BUDGET_LINE, SEPARATE_APPORTIONMENT -> line == null ? "linha da PO"
                    : line.code() + " " + line.description();
            case WITHOUT_BUDGET_LINE -> "sem linha da PO";
            case TO_REALLOCATE -> "a realocar";
            case ADJUSTMENTS -> "ajuste";
            case OTHER_FUNDS -> "pago pelo fundo " + fund.name();
            case BY_ACCOUNT -> "por conta do extrato";
            case TRANSFERS_IN -> "transferência recebida";
            case TRANSFERS_OUT -> "transferência enviada";
            case CONDO_FEE -> "cota recebida";
            case OTHER_INCOME -> "outras receitas";
            case OTHER_CREDITS -> "cotas extras e outros créditos";
            case OTHER_FUNDS_INCOME -> "entrada do fundo " + fund.name();
        };
    }

    private static void addOutflow(Destination d, LedgerEntry e, YearMonth m, Map<UUID, Row> lineRows, Rows blocks) {
        BigDecimal amount = e.getDebit();
        switch (d.code()) {
            case TRANSFERS_OUT -> {
                // in the transfers row, from the fund movement
            }
            case BUDGET_LINE -> {
                Row r = lineRows.get(d.lineId());
                if (r != null) {
                    r.add(m, amount);
                } else {
                    accountChild(blocks.get(CashFlowRowCode.WITHOUT_BUDGET_LINE, "Sem linha da PO"), e, m);
                }
            }
            case SEPARATE_APPORTIONMENT -> {
                Row block = blocks.get(CashFlowRowCode.SEPARATE_APPORTIONMENT, null);
                block.add(m, amount);
                Row line = lineRows.get(d.lineId());
                block.child("line:" + d.lineId(), () -> new Row(CashFlowRowCode.SEPARATE_APPORTIONMENT,
                        line == null ? null : line.label, d.lineId(), line == null ? null : line.lineCode, null, null))
                        .add(m, amount);
            }
            default -> accountChild(blocks.get(d.code(), null), e, m);
        }
    }

    private static void accountChild(Row block, LedgerEntry e, YearMonth m) {
        block.add(m, e.getDebit());
        String account = e.getAccountCode();
        block.child("account:" + account, () -> new Row(block.code, account == null ? "lançamento sem conta"
                : account + " " + Objects.requireNonNullElse(e.getAccountName(), ""), null, null, account, null))
                .add(m, e.getDebit());
    }

    /** Every line of the groups 1.1 to 1.8 of a month's budget vs. actual, in budget order (first seen wins). */
    private static void registerLines(Calculation calc, Map<UUID, Row> lineRows, Map<UUID, GroupInfo> groups) {
        for (BudgetVsActualGroupResponse g : calc.result().groups()) {
            GroupInfo info = groups.computeIfAbsent(g.lineId(), k -> new GroupInfo(g.lineId(), g.code(),
                    g.description()));
            for (BudgetVsActualLineResponse l : g.lines()) {
                if (l.mark() == BudgetLineMark.SEPARATE_APPORTIONMENT) {
                    lineRows.putIfAbsent(l.lineId(), new Row(CashFlowRowCode.SEPARATE_APPORTIONMENT,
                            l.code() + " " + l.description(), l.lineId(), l.code(), null, null));
                    continue;
                }
                if (!lineRows.containsKey(l.lineId())) {
                    lineRows.put(l.lineId(), new Row(CashFlowRowCode.BUDGET_LINE, l.code() + " " + l.description(),
                            l.lineId(), l.code(), null, null));
                    info.lines.add(l.lineId());
                }
            }
        }
    }

    private static Row fundRow(CashFlowRowCode code, FundMovementResponse fund) {
        return new Row(code, fund.name(), null, null, null, fund.fundId());
    }

    private static List<BigDecimal> sumCells(List<YearMonth> months, List<CashFlowRowResponse> rows,
            java.util.function.Predicate<YearMonth> applies) {
        List<BigDecimal> cells = new ArrayList<>();
        for (int i = 0; i < months.size(); i++) {
            if (!applies.test(months.get(i))) {
                cells.add(null);
                continue;
            }
            BigDecimal s = ZERO;
            for (CashFlowRowResponse r : rows) {
                if (r.cells().get(i) != null) {
                    s = s.add(r.cells().get(i));
                }
            }
            cells.add(s);
        }
        return java.util.Collections.unmodifiableList(cells);
    }

    private static BigDecimal total(List<BigDecimal> cells) {
        return cells.stream().filter(Objects::nonNull).reduce(BigDecimal::add).orElse(null);
    }

    /** Lookups of one calculation (mutable only in here). */
    private static final class Context {
        final Input in;
        final Map<String, FundMonthSummaryResponse> summaries = new HashMap<>();
        final Map<UUID, FundMovementResponse> funds = new LinkedHashMap<>();
        final Map<YearMonth, Map<UUID, Destination>> destinations = new HashMap<>();
        final Map<UUID, BudgetLineMark> marks = new HashMap<>();

        Context(Input in) {
            this.in = in;
            in.funds().months().forEach(s -> summaries.put(s.month(), s));
            in.funds().funds().forEach(f -> funds.put(f.fundId(), f));
            in.calculations().values().forEach(c -> c.result().groups().forEach(g -> g.lines()
                    .forEach(l -> marks.put(l.lineId(), l.mark()))));
        }

        FundMonthSummaryResponse summary(YearMonth m) {
            return summaries.get(m.toString());
        }

        UsedCashFlowResponse file(YearMonth m) {
            FundMonthSummaryResponse s = summary(m);
            return s == null || s.status() != MonthStatus.WITH_CASH_FLOW || s.cashFlows().size() != 1 ? null
                    : s.cashFlows().getFirst();
        }

        FundMovementResponse fund(UUID id) {
            return id == null ? null : funds.get(id);
        }

        List<FundMovementResponse> selectedFunds() {
            if (in.allFunds()) {
                return List.copyOf(funds.values());
            }
            FundMovementResponse f = fund(in.fundId());
            return f == null ? List.of() : List.of(f);
        }

        FundMonthMovementResponse movement(FundMovementResponse fund, YearMonth m) {
            return fund.months().stream().filter(x -> x.month().equals(m.toString())).findFirst().orElse(null);
        }

        boolean hasNumbers(YearMonth m) {
            return file(m) != null && selectedFunds().stream().anyMatch(f -> {
                FundMonthMovementResponse mv = movement(f, m);
                return mv != null && mv.present();
            });
        }

        Calculation calculation(YearMonth m) {
            return in.budgetMonths().contains(m) ? in.calculations().get(m) : null;
        }

        boolean includesOperating() {
            return in.allFunds() || Objects.equals(in.fundId(), in.operatingFundId());
        }

        /** The operating fund's outflows of the month go by budget line. */
        boolean byLine(YearMonth m) {
            return hasNumbers(m) && includesOperating() && in.reviewed() && calculation(m) != null;
        }

        /** Some outflows of the month go by cash flow account. */
        boolean byAccount(YearMonth m) {
            return hasNumbers(m) && (includesOperating() ? !byLine(m) : true);
        }

        List<LedgerEntry> entriesOf(UUID fundId, YearMonth m) {
            UsedCashFlowResponse file = file(m);
            if (file == null) {
                return List.of();
            }
            return in.entries().stream().filter(e -> e.getFileId().equals(file.fileId())
                    && e.getFundId().equals(fundId) && YearMonth.from(e.getDate()).equals(m))
                    .sorted(Comparator.comparing(LedgerEntry::getDate).thenComparing(LedgerEntry::getPage)
                            .thenComparing(LedgerEntry::getSequence).thenComparing(LedgerEntry::getId))
                    .toList();
        }

        /** Destination of a debit, the same rule as the table. */
        Destination destination(LedgerEntry e, YearMonth m) {
            boolean operating = e.getFundId().equals(in.operatingFundId());
            Calculation calc = operating ? calculation(m) : null;
            if (calc != null) {
                Destination d = destinations.computeIfAbsent(m, k -> destinations(calc)).get(e.getId());
                if (d != null && d.code() == CashFlowRowCode.TRANSFERS_OUT) {
                    return d;
                }
                if (byLine(m)) {
                    return d == null ? new Destination(CashFlowRowCode.WITHOUT_BUDGET_LINE, null) : d;
                }
                return new Destination(CashFlowRowCode.BY_ACCOUNT, null);
            }
            if (e.isInterFundTransfer()) {
                return new Destination(CashFlowRowCode.TRANSFERS_OUT, null);
            }
            return new Destination(!operating && in.allFunds() ? CashFlowRowCode.OTHER_FUNDS
                    : CashFlowRowCode.BY_ACCOUNT, null);
        }

        /** Destination of a credit, the same marks as the fund movements (Q25). */
        Destination creditDestination(LedgerEntry e, FundMovementResponse fund) {
            if (e.isInterFundTransfer()) {
                return new Destination(CashFlowRowCode.TRANSFERS_IN, null);
            }
            if (in.allFunds() && !fund.fundId().equals(in.operatingFundId())) {
                return new Destination(CashFlowRowCode.OTHER_FUNDS_INCOME, null);
            }
            Boolean fee = e.getCondoFeeReceipt();
            return new Destination(fee == null ? CashFlowRowCode.OTHER_CREDITS
                    : fee ? CashFlowRowCode.CONDO_FEE : CashFlowRowCode.OTHER_INCOME, null);
        }

        private Map<UUID, Destination> destinations(Calculation calc) {
            Map<UUID, Destination> m = new HashMap<>();
            for (var e : calc.evidence().entrySet()) {
                String key = e.getKey();
                Destination d;
                if (key.startsWith("line:")) {
                    UUID line = UUID.fromString(key.substring("line:".length()));
                    d = new Destination(marks.get(line) == BudgetLineMark.SEPARATE_APPORTIONMENT
                            ? CashFlowRowCode.SEPARATE_APPORTIONMENT : CashFlowRowCode.BUDGET_LINE, line);
                } else {
                    d = switch (key) {
                        case BudgetVsActualCalculator.TARGET_ADJUSTMENTS ->
                                new Destination(CashFlowRowCode.ADJUSTMENTS, null);
                        case BudgetVsActualCalculator.TARGET_TO_REALLOCATE ->
                                new Destination(CashFlowRowCode.TO_REALLOCATE, null);
                        case BudgetVsActualCalculator.TARGET_WITHOUT_BUDGET_LINE ->
                                new Destination(CashFlowRowCode.WITHOUT_BUDGET_LINE, null);
                        case BudgetVsActualCalculator.TARGET_TRANSFERS ->
                                new Destination(CashFlowRowCode.TRANSFERS_OUT, null);
                        default -> null;
                    };
                }
                if (d != null) {
                    for (EvidenceResponse ev : e.getValue()) {
                        m.putIfAbsent(ev.entryId(), d);
                    }
                }
            }
            return m;
        }
    }

    private static final class GroupInfo {
        final UUID lineId;
        final String code;
        final String description;
        final List<UUID> lines = new ArrayList<>();

        GroupInfo(UUID lineId, String code, String description) {
            this.lineId = lineId;
            this.code = code;
            this.description = description;
        }
    }

    /** Rows of a section, by code, in insertion order. */
    private static final class Rows {
        final Map<CashFlowRowCode, Row> rows = new LinkedHashMap<>();

        Row get(CashFlowRowCode code, String label) {
            return rows.computeIfAbsent(code, k -> new Row(code, label, null, null, null, null));
        }

        BigDecimal sum(YearMonth m) {
            return rows.values().stream().map(r -> r.value(m)).reduce(ZERO, BigDecimal::add);
        }

        BigDecimal value(CashFlowRowCode code, YearMonth m) {
            Row r = rows.get(code);
            return r == null ? ZERO : r.value(m);
        }

        List<CashFlowRowResponse> responses(List<YearMonth> months, java.util.function.Predicate<YearMonth> applies) {
            return rows.values().stream().map(r -> r.response(months, applies)).toList();
        }
    }

    private static final class Row {
        final CashFlowRowCode code;
        final String label;
        final UUID lineId;
        final String lineCode;
        final String account;
        final UUID fundId;
        final Map<YearMonth, BigDecimal> values = new HashMap<>();
        final Map<String, Row> children = new TreeMap<>(Comparator.nullsLast(Comparator.naturalOrder()));

        Row(CashFlowRowCode code, String label, UUID lineId, String lineCode, String account, UUID fundId) {
            this.code = code;
            this.label = label;
            this.lineId = lineId;
            this.lineCode = lineCode;
            this.account = account;
            this.fundId = fundId;
        }

        void add(YearMonth m, BigDecimal amount) {
            values.merge(m, amount == null ? ZERO : amount, BigDecimal::add);
        }

        BigDecimal value(YearMonth m) {
            return values.getOrDefault(m, ZERO);
        }

        Row child(String key, java.util.function.Supplier<Row> create) {
            return children.computeIfAbsent(key, k -> create.get());
        }

        CashFlowRowResponse response(List<YearMonth> months, java.util.function.Predicate<YearMonth> applies) {
            List<BigDecimal> cells = new ArrayList<>();
            for (YearMonth m : months) {
                cells.add(applies.test(m) ? values.getOrDefault(m, ZERO) : null);
            }
            List<CashFlowRowResponse> kids = children.values().stream().map(c -> c.response(months, applies))
                    .toList();
            List<BigDecimal> unmodifiable = java.util.Collections.unmodifiableList(cells);
            return new CashFlowRowResponse(code, label, lineId, lineCode, account, fundId, unmodifiable,
                    total(unmodifiable), kids);
        }
    }
}
