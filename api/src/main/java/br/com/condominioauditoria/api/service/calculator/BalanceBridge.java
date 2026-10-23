package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.dto.response.accounting.FundMonthMovementResponse;
import br.com.condominioauditoria.api.dto.response.accounting.FundMonthSummaryResponse;
import br.com.condominioauditoria.api.dto.response.accounting.FundMovementResponse;
import br.com.condominioauditoria.api.dto.response.accounting.FundMovementsResponse;
import br.com.condominioauditoria.api.dto.response.budget.BalanceBridgeResponse;
import br.com.condominioauditoria.api.dto.response.budget.BridgeFundResponse;
import br.com.condominioauditoria.api.dto.response.budget.BridgeItemResponse;
import br.com.condominioauditoria.api.dto.response.budget.BridgeLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.BridgeMappingResponse;
import br.com.condominioauditoria.api.dto.response.budget.BridgeMonthItemResponse;
import br.com.condominioauditoria.api.dto.response.budget.BridgeMonthResponse;
import br.com.condominioauditoria.api.dto.response.budget.BridgePartialMonthResponse;
import br.com.condominioauditoria.api.dto.response.budget.BridgePartResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetBriefResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetTrackingResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualWarningResponse;
import br.com.condominioauditoria.api.dto.response.budget.EntryBlockResponse;
import br.com.condominioauditoria.api.dto.response.budget.TrackingBlockCellResponse;
import br.com.condominioauditoria.api.dto.response.budget.TrackingBlockResponse;
import br.com.condominioauditoria.api.dto.response.budget.TrackingCellResponse;
import br.com.condominioauditoria.api.dto.response.budget.TrackingLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.TrackingMonthResponse;
import br.com.condominioauditoria.api.model.enums.BalanceBridgeItemCode;
import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import br.com.condominioauditoria.api.model.enums.MonthStatus;
import br.com.condominioauditoria.api.util.MoneyFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Balance bridge (RF-13.6 and RF-13.7; ADR 0007, Decision 3). <b>Pure function</b> over the tracking grid
 * ({@link BudgetTrackingGrid}) and the fund movements ({@link FundMovements}) of the same months. For the chosen fund
 * (or all funds), the items add up <b>exactly</b> to the variation of the printed balance, cent by cent:
 * <ol>
 * <li>{@code LINE_SAVINGS}: planned − actual of the MONTHLY and ANNUAL lines (operating fund);</li>
 * <li>{@code PROVISIONS_RESERVED}: the same for the PROVISION lines (Q44), never counted as savings;</li>
 * <li>{@code SEPARATE_APPORTIONMENT}: the same for the separate apportionment lines (Q45);</li>
 * <li>{@code INCOME_VS_PLANNED}: fee received + other income − planned amount of the period (the operating fund's
 * planned expense; for a 1.9 fund, its line);</li>
 * <li>{@code FUND_PLANNED_COLLECTION} and {@code FUND_EXPENSES}: in the bridge of one fund other than the operating
 * fund, the planned collection of the 1.9 line that stays in the fund and the debits that are not transfers;</li>
 * <li>{@code OUTSIDE_BUDGET}: to reallocate, without budget line and adjustments, negative when they leave the fund;</li>
 * <li>{@code INTER_FUND_TRANSFERS}: received − sent;</li>
 * <li>{@code APPORTIONMENT_FUNDS} (only in the bridge of all funds, Q49): credits − debits of every fund that is not
 * the operating fund, without the transfers between funds, with the list by fund and without a share of the variation.
 * In that bridge {@code INCOME_VS_PLANNED} uses only the operating fund ("cota ordinária acima ou abaixo do previsto")
 * and the 1.9 planned collection is in no item;</li>
 * <li>{@code UNEXPLAINED}: real variation − sum of the items above. Should be zero; otherwise "a ponte não fecha".</li>
 * </ol>
 * The total covers the complete months only; a partial month (RF-13.10) has its own column, outside the total, with only
 * the inflows, outflows, balances and variation: its items are null until the month closes (Q50). The
 * opening balance is the closing balance of the month before the period when that cash flow was loaded (P8), otherwise
 * the opening balance printed in the first month. All amounts are BigDecimal with 2 decimals.
 */
public final class BalanceBridge {

    /**
     * 2: Q49 (apportionment funds apart in the bridge of all funds) and Q50 (month in progress without items).
     * 3: account mapping state as fields ({@code mapping}), with the provisional flag.
     */
    public static final String VERSION = "3";

    /**
     * Above this share (in %) of the operating fund's debits in "sem linha da PO" (accounts without a confirmed
     * mapping), the bridge is flagged {@code provisional}: those debits are outside the budget lines, so "Economia nas
     * linhas" keeps their planned amount and "Fora da PO" keeps their actual amount, and both mean little until the
     * mapping is confirmed. The bridge still closes. The only place of this limit; the screen reads it from the API.
     */
    public static final BigDecimal PROVISIONAL_UNMAPPED_SHARE = new BigDecimal("5.00");

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * @param fundId chosen fund; null = all funds
     * @param funds fund movements from the month before {@code from} to {@code to}
     * @param withoutBudgetLine "sem linha da PO" block of the operating fund by month (accounts without a confirmed
     *        mapping; {@link BudgetTrackingGrid#withoutBudgetLineByMonth}), for the NO_CONFIRMED_MAPPING warning
     */
    public record Input(BudgetTrackingResponse grid, FundMovementsResponse funds, UUID fundId, UUID operatingFundId,
            YearMonth from, YearMonth to, Map<YearMonth, EntryBlockResponse> withoutBudgetLine) {

        public Input {
            Objects.requireNonNull(grid, "grid");
            Objects.requireNonNull(funds, "funds");
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            withoutBudgetLine = withoutBudgetLine == null ? Map.of() : Map.copyOf(withoutBudgetLine);
        }

        /** Input from the whole grid: the response and the "sem linha da PO" block of each month. */
        public static Input of(BudgetTrackingGrid.Grid grid, FundMovementsResponse funds, UUID fundId,
                UUID operatingFundId, YearMonth from, YearMonth to) {
            return new Input(grid.result(), funds, fundId, operatingFundId, from, to,
                    BudgetTrackingGrid.withoutBudgetLineByMonth(grid.months()));
        }
    }

    private BalanceBridge() {
    }

    public static BalanceBridgeResponse calculate(Input in) {
        boolean all = in.fundId() == null;
        String fundName = all ? "Todos os fundos" : in.funds().funds().stream()
                .filter(f -> f.fundId().equals(in.fundId())).map(FundMovementResponse::name).findFirst().orElse(null);
        boolean withOperating = all || in.fundId().equals(in.operatingFundId());
        boolean withOthers = all || !withOperating;
        List<BudgetVsActualWarningResponse> warnings = new ArrayList<>();
        BudgetBriefResponse budget = in.grid().budget();
        if (withOperating && in.operatingFundId() == null) {
            return empty(in, all, fundName, BudgetVsActualStatus.NO_OPERATING_FUND,
                    "Confirme o fundo ordinário (fundo Condomínio) para calcular a origem do saldo");
        }

        Map<String, FundMonthSummaryResponse> summaries = new LinkedHashMap<>();
        in.funds().months().forEach(m -> summaries.put(m.month(), m));
        Map<String, TrackingMonthResponse> gridMonths = new LinkedHashMap<>();
        in.grid().months().forEach(m -> gridMonths.put(m.month(), m));
        List<TrackingLineResponse> lines = in.grid().groups().stream().flatMap(g -> g.lines().stream()).toList();

        Totals total = new Totals();
        List<BridgeMonthResponse> months = new ArrayList<>();
        BigDecimal opening = null;
        String openingSource = null;
        BigDecimal closing = null;
        YearMonth firstInTotal = null;
        YearMonth lastInTotal = null;
        List<EntryBlockResponse> unmappedBlocks = new ArrayList<>();
        BigDecimal operatingDebits = ZERO;
        List<String> gaps = new ArrayList<>();
        YearMonth previous = in.from().minusMonths(1);
        FundMonthSummaryResponse before = summaries.get(previous.toString());
        if (before != null && before.status() == MonthStatus.WITH_CASH_FLOW && before.partialUntil() == null) {
            BigDecimal b = balance(in, all, previous, true);
            if (b != null) {
                opening = b;
                openingSource = "saldo final do extrato de " + BudgetVsActualCalculator.mmyyyy(previous);
                firstInTotal = in.from();
            }
        }
        for (YearMonth m = in.from(); !m.isAfter(in.to()); m = m.plusMonths(1)) {
            FundMonthSummaryResponse s = summaries.get(m.toString());
            if (s == null || s.status() != MonthStatus.WITH_CASH_FLOW) {
                MonthStatus status = s == null ? MonthStatus.NO_CASH_FLOW : s.status();
                months.add(new BridgeMonthResponse(m.toString(), status, null, false, null, null, null, List.of(), null,
                        null));
                if (lastInTotal != null || opening != null) {
                    gaps.add(m.toString());
                }
                continue;
            }
            boolean inTotal = s.partialUntil() == null;
            Totals t = new Totals();
            if (withOperating) {
                operating(in, m, gridMonths.get(m.toString()), lines, t);
            }
            if (all) {
                apportionmentFunds(in, m, t);
            } else if (withOthers) {
                others(in, m, t);
            }
            BigDecimal o = balance(in, all, m, false);
            BigDecimal c = balance(in, all, m, true);
            BigDecimal variation = o == null || c == null ? null : c.subtract(o);
            t.unexplained = variation == null ? null : variation.subtract(t.sum());
            BigDecimal[] flows = flows(in, all, m);
            // A month in progress shows only what came in and went out (Q50): no item compares it with the planned
            months.add(new BridgeMonthResponse(m.toString(), s.status(), s.partialUntil(), inTotal, o, c, variation,
                    inTotal ? t.monthItems(withOperating, withOthers, all) : Totals.blankItems(withOperating,
                            withOthers, all), flows[0], flows[1]));
            if (inTotal) {
                if (opening == null && lastInTotal == null) {
                    opening = o;
                    openingSource = "saldo inicial impresso no extrato de " + BudgetVsActualCalculator.mmyyyy(m);
                }
                if (firstInTotal == null) {
                    firstInTotal = m;
                }
                EntryBlockResponse unmapped = in.withoutBudgetLine().get(m);
                if (withOperating && unmapped != null) {
                    unmappedBlocks.add(unmapped);
                }
                FundMonthMovementResponse op = withOperating ? movement(in, in.operatingFundId(), m) : null;
                if (op != null) {
                    operatingDebits = operatingDebits.add(op.debits().subtract(op.transfersOut()));
                }
                total.add(t);
                closing = c;
                lastInTotal = m;
            }
        }
        // Months without a cash flow after the last complete month are not gaps
        YearMonth last = lastInTotal;
        gaps.removeIf(g -> last == null || !YearMonth.parse(g).isBefore(last));
        if (lastInTotal == null) {
            BudgetVsActualStatus status = in.grid().status() == BudgetVsActualStatus.CALCULATED
                    || in.grid().status() == BudgetVsActualStatus.NO_CASH_FLOW ? BudgetVsActualStatus.NO_CASH_FLOW
                    : in.grid().status();
            BalanceBridgeResponse e = empty(in, all, fundName, status, status == BudgetVsActualStatus.NO_CASH_FLOW
                    ? "Nenhum mês completo com extrato no período" : in.grid().message());
            return new BalanceBridgeResponse(e.calculationVersion(), e.status(), e.message(), e.fundId(),
                    e.fundName(), e.allFunds(), e.from(), e.to(), budget, null, null, null, null, null, List.of(),
                    List.copyOf(months), List.of(), null, null, null, null, partialMonth(months), null);
        }
        BigDecimal variation = opening == null || closing == null ? null : closing.subtract(opening);
        total.unexplained = variation == null ? null : variation.subtract(total.sum());
        Boolean closes = total.unexplained == null ? null : total.unexplained.signum() == 0;
        if (!gaps.isEmpty()) {
            warnings.add(new BudgetVsActualWarningResponse("MONTHS_WITHOUT_CASH_FLOW",
                    BudgetVsActualCalculator.monthList(gaps) + " sem extrato carregado: a variação desses meses fica"
                            + " em \"não explicado\""));
        }
        if (variation == null) {
            warnings.add(new BudgetVsActualWarningResponse("BALANCE_NOT_PRINTED",
                    "Saldo inicial ou final do fundo não impresso no extrato: a variação não foi apurada"));
        } else if (!closes) {
            warnings.add(new BudgetVsActualWarningResponse("BRIDGE_DOES_NOT_CLOSE", "a ponte não fecha: R$ "
                    + MoneyFormatter.format(total.unexplained) + " não explicado"));
        }
        LocalDate closingDate = lastInTotal.atEndOfMonth();
        months.stream().filter(m -> m.partialUntil() != null).forEach(m -> warnings.add(
                new BudgetVsActualWarningResponse("PARTIAL_MONTH", BudgetVsActualCalculator.mmyyyy(
                        YearMonth.parse(m.month())) + " parcial: aparece na sua coluna, fora do total"
                        + " (lançamentos até " + DAY.format(m.partialUntil())
                        + (m.variation() == null ? "" : "; variação de R$ " + MoneyFormatter.format(m.variation())
                                + " à parte") + "); o total vai até " + DAY.format(closingDate))));
        BridgeMappingResponse mapping = null;
        if (withOperating) {
            BudgetTrackingGrid.UnmappedAccounts unmapped = BudgetTrackingGrid.unmapped(unmappedBlocks);
            BudgetTrackingGrid.unmappedWarning(unmapped, " no total da ponte",
                    "ficam em \"Fora da PO\" e \"Economia nas linhas\" pode incluir o previsto dessas despesas")
                    .ifPresent(warnings::add);
            mapping = mapping(unmapped, operatingDebits);
        }
        if (in.grid().warnings().stream().anyMatch(w -> w.code().equals("PROFILE_NOT_CONFIRMED")) && withOperating) {
            warnings.add(new BudgetVsActualWarningResponse("PROFILE_NOT_CONFIRMED",
                    "Há linhas com tipo não confirmado: valem \"mensal\" e entram em \"economia nas linhas\""));
        }
        return new BalanceBridgeResponse(VERSION, BudgetVsActualStatus.CALCULATED, null, in.fundId(), fundName, all,
                in.from().toString(), in.to().toString(), budget, opening, openingSource, closing, variation, closes,
                total.items(withOperating, withOthers, all, variation), List.copyOf(months), List.copyOf(warnings),
                firstInTotal.toString(), lastInTotal.toString(), firstInTotal.minusMonths(1).atEndOfMonth(),
                closingDate, partialMonth(months), mapping);
    }

    /**
     * Mapping state of the total: provisional when the amount without a confirmed mapping is above
     * {@link #PROVISIONAL_UNMAPPED_SHARE}% of the operating fund's debits (any amount, with no debits).
     */
    static BridgeMappingResponse mapping(BudgetTrackingGrid.UnmappedAccounts unmapped, BigDecimal debits) {
        BigDecimal amount = unmapped.total().setScale(2, RoundingMode.HALF_UP);
        BigDecimal limit = debits.multiply(PROVISIONAL_UNMAPPED_SHARE).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        boolean provisional = unmapped.count() > 0 && amount.compareTo(limit) > 0;
        return new BridgeMappingResponse((int) unmapped.count(), amount, unmapped.entries(),
                debits.setScale(2, RoundingMode.HALF_UP), PROVISIONAL_UNMAPPED_SHARE, provisional,
                List.copyOf(unmapped.accounts()));
    }

    /** Operating fund items of a month: line balances by profile, income, outside the budget and transfers. */
    private static void operating(Input in, YearMonth m, TrackingMonthResponse gm, List<TrackingLineResponse> lines,
            Totals t) {
        FundMonthMovementResponse f = movement(in, in.operatingFundId(), m);
        if (f == null) {
            return;
        }
        String month = m.toString();
        BigDecimal planned = ZERO;
        if (gm != null && gm.planned() != null) {
            planned = gm.planned();
            for (TrackingLineResponse l : lines) {
                TrackingCellResponse cell = l.cells().stream().filter(c -> c.month().equals(month)).findFirst()
                        .orElse(null);
                if (cell == null || cell.planned() == null) {
                    continue;
                }
                BigDecimal amount = cell.planned().subtract(cell.actual());
                BalanceBridgeItemCode code = switch (l.effectiveProfile()) {
                    case PROVISION -> BalanceBridgeItemCode.PROVISIONS_RESERVED;
                    case SEPARATE_APPORTIONMENT -> BalanceBridgeItemCode.SEPARATE_APPORTIONMENT;
                    case MONTHLY, ANNUAL -> BalanceBridgeItemCode.LINE_SAVINGS;
                };
                t.line(code, l, amount);
            }
            t.toReallocate = t.toReallocate.subtract(block(in.grid().blocks().toReallocate(), month));
            t.withoutLine = t.withoutLine.subtract(block(in.grid().blocks().withoutBudgetLine(), month));
            t.adjustments = t.adjustments.subtract(block(in.grid().blocks().adjustments(), month));
            // Debits of the operating fund that the budget vs. actual did not split stay in the unexplained item
        }
        t.condoFee = t.condoFee.add(f.condoFee());
        t.otherIncome = t.otherIncome.add(f.otherIncome());
        t.planned = t.planned.subtract(planned);
        t.transfersIn = t.transfersIn.add(f.transfersIn());
        t.transfersOut = t.transfersOut.subtract(f.transfersOut());
    }

    /** Items of one fund other than the operating fund: income, planned 1.9 collection, its expenses and transfers. */
    private static void others(Input in, YearMonth m, Totals t) {
        for (FundMovementResponse fund : in.funds().funds()) {
            if (fund.operatingFund() || !fund.fundId().equals(in.fundId())) {
                continue;
            }
            FundMonthMovementResponse f = movement(in, fund.fundId(), m);
            if (f == null) {
                continue;
            }
            BigDecimal planned = f.planned() == null ? ZERO : f.planned();
            t.condoFee = t.condoFee.add(f.condoFee());
            t.otherIncome = t.otherIncome.add(f.otherIncome());
            t.planned = t.planned.subtract(planned);
            t.fundPlanned = t.fundPlanned.add(planned);
            t.fundExpenses = t.fundExpenses.subtract(f.expenses()).subtract(f.otherDebits());
            t.transfersIn = t.transfersIn.add(f.transfersIn());
            t.transfersOut = t.transfersOut.subtract(f.transfersOut());
        }
    }

    /**
     * Apportionment funds of the bridge of all funds (Q49): every fund that is not the operating fund. Credits and debits
     * without the transfers between funds (those stay in INTER_FUND_TRANSFERS); the 1.9 planned amount is in no item.
     */
    private static void apportionmentFunds(Input in, YearMonth m, Totals t) {
        for (FundMovementResponse fund : in.funds().funds()) {
            if (fund.operatingFund() || fund.fundId().equals(in.operatingFundId())) {
                continue;
            }
            FundMonthMovementResponse f = movement(in, fund.fundId(), m);
            if (f == null) {
                continue;
            }
            t.fund(fund, f.credits().subtract(f.transfersIn()), f.debits().subtract(f.transfersOut()));
            t.transfersIn = t.transfersIn.add(f.transfersIn());
            t.transfersOut = t.transfersOut.subtract(f.transfersOut());
        }
    }

    /** Credits and debits of the entries of the chosen fund (or of every fund) in the month; null without the fund. */
    private static BigDecimal[] flows(Input in, boolean all, YearMonth m) {
        BigDecimal inflows = null;
        BigDecimal outflows = null;
        for (FundMovementResponse fund : in.funds().funds()) {
            if (!all && !fund.fundId().equals(in.fundId())) {
                continue;
            }
            FundMonthMovementResponse f = movement(in, fund.fundId(), m);
            if (f == null) {
                continue;
            }
            inflows = (inflows == null ? ZERO : inflows).add(f.credits());
            outflows = (outflows == null ? ZERO : outflows).add(f.debits());
        }
        return new BigDecimal[] {inflows, outflows};
    }

    private static FundMonthMovementResponse movement(Input in, UUID fundId, YearMonth m) {
        return in.funds().funds().stream().filter(f -> f.fundId().equals(fundId)).findFirst()
                .flatMap(f -> f.months().stream().filter(x -> x.month().equals(m.toString()) && x.present())
                        .findFirst()).orElse(null);
    }

    /** Printed opening or closing balance of the chosen fund (or the sum of every fund printed in the month). */
    private static BigDecimal balance(Input in, boolean all, YearMonth m, boolean closingBalance) {
        BigDecimal sum = null;
        for (FundMovementResponse fund : in.funds().funds()) {
            if (!all && !fund.fundId().equals(in.fundId())) {
                continue;
            }
            FundMonthMovementResponse f = movement(in, fund.fundId(), m);
            if (f == null) {
                continue;
            }
            BigDecimal b = closingBalance ? f.closingBalance() : f.openingBalance();
            if (b == null) {
                return null;
            }
            sum = sum == null ? b : sum.add(b);
        }
        return sum;
    }

    private static BigDecimal block(TrackingBlockResponse block, String month) {
        return block.cells().stream().filter(c -> c.month().equals(month)).map(TrackingBlockCellResponse::amount)
                .filter(Objects::nonNull).findFirst().orElse(ZERO);
    }

    private static BalanceBridgeResponse empty(Input in, boolean all, String fundName, BudgetVsActualStatus status,
            String message) {
        return new BalanceBridgeResponse(VERSION, status, message, in.fundId(), fundName, all, in.from().toString(),
                in.to().toString(), in.grid().budget(), null, null, null, null, null, List.of(), List.of(), List.of(),
                null, null, null, null, null, null);
    }

    /** The last partial month of the period (RF-13.10), apart from the total; null without one. */
    private static BridgePartialMonthResponse partialMonth(List<BridgeMonthResponse> months) {
        return months.stream().filter(m -> m.partialUntil() != null).reduce((a, b) -> b)
                .map(m -> new BridgePartialMonthResponse(m.month(), m.partialUntil(), m.openingBalance(),
                        m.closingBalance(), m.variation(), m.inflows(), m.outflows()))
                .orElse(null);
    }

    public static BigDecimal percentage(BigDecimal amount, BigDecimal variation) {
        if (amount == null || variation == null || variation.signum() == 0) {
            return null;
        }
        return amount.multiply(HUNDRED).divide(variation, 10, RoundingMode.HALF_UP).setScale(1, RoundingMode.HALF_UP);
    }

    /** Sums of a month or of the period (mutable only in here). Amounts already carry the sign of the bridge. */
    private static final class Totals {
        final Map<BalanceBridgeItemCode, Map<UUID, LineAmount>> lines = new EnumMap<>(BalanceBridgeItemCode.class);
        BigDecimal condoFee = ZERO;
        BigDecimal otherIncome = ZERO;
        BigDecimal planned = ZERO;
        BigDecimal fundPlanned = ZERO;
        BigDecimal fundExpenses = ZERO;
        BigDecimal toReallocate = ZERO;
        BigDecimal withoutLine = ZERO;
        BigDecimal adjustments = ZERO;
        BigDecimal transfersIn = ZERO;
        BigDecimal transfersOut = ZERO;
        final Map<UUID, FundAmount> apportionment = new LinkedHashMap<>();
        BigDecimal unexplained;

        void fund(FundMovementResponse fund, BigDecimal inflows, BigDecimal outflows) {
            apportionment.merge(fund.fundId(), new FundAmount(fund.fundId(), fund.name(), inflows, outflows),
                    FundAmount::plus);
        }

        BigDecimal apportionmentInflows() {
            return apportionment.values().stream().map(FundAmount::inflows).reduce(ZERO, BigDecimal::add);
        }

        BigDecimal apportionmentOutflows() {
            return apportionment.values().stream().map(FundAmount::outflows).reduce(ZERO, BigDecimal::add);
        }

        void line(BalanceBridgeItemCode code, TrackingLineResponse l, BigDecimal amount) {
            lines.computeIfAbsent(code, k -> new LinkedHashMap<>()).merge(l.lineId(),
                    new LineAmount(l, amount), (a, b) -> new LineAmount(a.line(), a.amount().add(b.amount())));
        }

        void add(Totals t) {
            t.lines.forEach((code, byLine) -> byLine.values().forEach(v -> line(code, v.line(), v.amount())));
            condoFee = condoFee.add(t.condoFee);
            otherIncome = otherIncome.add(t.otherIncome);
            planned = planned.add(t.planned);
            fundPlanned = fundPlanned.add(t.fundPlanned);
            fundExpenses = fundExpenses.add(t.fundExpenses);
            toReallocate = toReallocate.add(t.toReallocate);
            withoutLine = withoutLine.add(t.withoutLine);
            adjustments = adjustments.add(t.adjustments);
            transfersIn = transfersIn.add(t.transfersIn);
            transfersOut = transfersOut.add(t.transfersOut);
            t.apportionment.values().forEach(f -> apportionment.merge(f.fundId(), f, FundAmount::plus));
        }

        BigDecimal lineTotal(BalanceBridgeItemCode code) {
            return lines.getOrDefault(code, Map.of()).values().stream().map(LineAmount::amount)
                    .reduce(ZERO, BigDecimal::add);
        }

        BigDecimal amount(BalanceBridgeItemCode code) {
            return switch (code) {
                case LINE_SAVINGS, PROVISIONS_RESERVED, SEPARATE_APPORTIONMENT -> lineTotal(code);
                case INCOME_VS_PLANNED -> condoFee.add(otherIncome).add(planned);
                case FUND_PLANNED_COLLECTION -> fundPlanned;
                case FUND_EXPENSES -> fundExpenses;
                case OUTSIDE_BUDGET -> toReallocate.add(withoutLine).add(adjustments);
                case INTER_FUND_TRANSFERS -> transfersIn.add(transfersOut);
                case APPORTIONMENT_FUNDS -> apportionmentInflows().subtract(apportionmentOutflows());
                case UNEXPLAINED -> unexplained;
            };
        }

        BigDecimal sum() {
            BigDecimal s = ZERO;
            for (BalanceBridgeItemCode code : BalanceBridgeItemCode.values()) {
                if (code != BalanceBridgeItemCode.UNEXPLAINED) {
                    s = s.add(amount(code));
                }
            }
            return s;
        }

        static List<BalanceBridgeItemCode> codes(boolean withOperating, boolean withOthers, boolean all) {
            List<BalanceBridgeItemCode> codes = new ArrayList<>();
            if (withOperating) {
                codes.addAll(List.of(BalanceBridgeItemCode.LINE_SAVINGS, BalanceBridgeItemCode.PROVISIONS_RESERVED,
                        BalanceBridgeItemCode.SEPARATE_APPORTIONMENT));
            }
            codes.add(BalanceBridgeItemCode.INCOME_VS_PLANNED);
            if (withOthers && !all) {
                codes.addAll(List.of(BalanceBridgeItemCode.FUND_PLANNED_COLLECTION,
                        BalanceBridgeItemCode.FUND_EXPENSES));
            }
            if (withOperating) {
                codes.add(BalanceBridgeItemCode.OUTSIDE_BUDGET);
            }
            codes.add(BalanceBridgeItemCode.INTER_FUND_TRANSFERS);
            if (all) {
                codes.add(BalanceBridgeItemCode.APPORTIONMENT_FUNDS);
            }
            codes.add(BalanceBridgeItemCode.UNEXPLAINED);
            return codes;
        }

        List<BridgeMonthItemResponse> monthItems(boolean withOperating, boolean withOthers, boolean all) {
            return codes(withOperating, withOthers, all).stream()
                    .map(c -> new BridgeMonthItemResponse(c, amount(c))).toList();
        }

        /** The items of a month in progress (Q50): every amount null ("—" on the screen). */
        static List<BridgeMonthItemResponse> blankItems(boolean withOperating, boolean withOthers, boolean all) {
            return codes(withOperating, withOthers, all).stream().map(c -> new BridgeMonthItemResponse(c, null))
                    .toList();
        }

        List<BridgeItemResponse> items(boolean withOperating, boolean withOthers, boolean all, BigDecimal variation) {
            List<BridgeItemResponse> items = new ArrayList<>();
            for (BalanceBridgeItemCode code : codes(withOperating, withOthers, all)) {
                BigDecimal amount = amount(code);
                // The apportionment funds have no share of the variation (Q49): the other shares do not add up to 100%
                BigDecimal share = code == BalanceBridgeItemCode.APPORTIONMENT_FUNDS ? null
                        : percentage(amount, variation);
                items.add(new BridgeItemResponse(code, label(code, all), amount, share, parts(code), lineList(code),
                        fundList(code)));
            }
            return List.copyOf(items);
        }

        private List<BridgePartResponse> parts(BalanceBridgeItemCode code) {
            return switch (code) {
                case INCOME_VS_PLANNED -> List.of(new BridgePartResponse("CONDO_FEE", "Cota recebida", condoFee),
                        new BridgePartResponse("OTHER_INCOME", "Outras receitas", otherIncome),
                        new BridgePartResponse("PLANNED", "Previsto do período", planned));
                case OUTSIDE_BUDGET -> List.of(new BridgePartResponse("TO_REALLOCATE", "A realocar", toReallocate),
                        new BridgePartResponse("WITHOUT_BUDGET_LINE", "Sem linha da PO", withoutLine),
                        new BridgePartResponse("ADJUSTMENTS", "Ajustes", adjustments));
                case INTER_FUND_TRANSFERS -> List.of(new BridgePartResponse("RECEIVED", "Recebidas", transfersIn),
                        new BridgePartResponse("SENT", "Enviadas", transfersOut));
                case APPORTIONMENT_FUNDS -> List.of(
                        new BridgePartResponse("INFLOWS", "Entradas", apportionmentInflows()),
                        new BridgePartResponse("OUTFLOWS", "Saídas", apportionmentOutflows().negate()));
                default -> List.of();
            };
        }

        private List<BridgeLineResponse> lineList(BalanceBridgeItemCode code) {
            return lines.getOrDefault(code, Map.of()).values().stream()
                    .sorted(Comparator.comparing(LineAmount::amount).reversed()
                            .thenComparingInt(a -> a.line().position()))
                    .map(a -> new BridgeLineResponse(a.line().lineId(), a.line().code(), a.line().description(),
                            a.line().effectiveProfile(), a.amount()))
                    .toList();
        }

        private List<BridgeFundResponse> fundList(BalanceBridgeItemCode code) {
            if (code != BalanceBridgeItemCode.APPORTIONMENT_FUNDS) {
                return List.of();
            }
            return apportionment.values().stream()
                    .sorted(Comparator.comparing(FundAmount::amount).reversed()
                            .thenComparing(f -> Objects.requireNonNullElse(f.name(), ""))
                            .thenComparing(f -> f.fundId().toString()))
                    .map(f -> new BridgeFundResponse(f.fundId(), f.name(), f.inflows(), f.outflows(), f.amount()))
                    .toList();
        }
    }

    private record LineAmount(TrackingLineResponse line, BigDecimal amount) {
    }

    /** Credits and debits of an apportionment fund without the transfers (Q49). */
    private record FundAmount(UUID fundId, String name, BigDecimal inflows, BigDecimal outflows) {

        BigDecimal amount() {
            return inflows.subtract(outflows);
        }

        FundAmount plus(FundAmount o) {
            return new FundAmount(fundId, name, inflows.add(o.inflows()), outflows.add(o.outflows()));
        }
    }

    /** Screen label of each item (Portuguese, without any explanation of the cause). */
    public static String label(BalanceBridgeItemCode code) {
        return label(code, false);
    }

    /** Screen label of each item; {@code allFunds}: the bridge of all funds, where the income is the operating fee. */
    public static String label(BalanceBridgeItemCode code, boolean allFunds) {
        if (allFunds && code == BalanceBridgeItemCode.INCOME_VS_PLANNED) {
            return "Cota ordinária acima ou abaixo do previsto";
        }
        return switch (code) {
            case LINE_SAVINGS -> "Economia nas linhas";
            case PROVISIONS_RESERVED -> "Reservado nas provisões: previsto para pagamento futuro (férias, rescisão,"
                    + " 13º)";
            case SEPARATE_APPORTIONMENT -> "Rateio à parte";
            case INCOME_VS_PLANNED -> "Receita acima ou abaixo do previsto";
            case FUND_PLANNED_COLLECTION -> "Arrecadação prevista dos fundos (linhas 1.9)";
            case FUND_EXPENSES -> "Débitos dos outros fundos";
            case OUTSIDE_BUDGET -> "Fora da PO";
            case INTER_FUND_TRANSFERS -> "Transferências entre fundos (líquido)";
            case APPORTIONMENT_FUNDS -> "Fundos de rateio: entradas − saídas";
            case UNEXPLAINED -> "Não explicado";
        };
    }
}
