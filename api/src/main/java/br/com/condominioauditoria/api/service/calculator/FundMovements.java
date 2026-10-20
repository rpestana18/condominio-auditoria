package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.dto.response.accounting.FundMonthMovementResponse;
import br.com.condominioauditoria.api.dto.response.accounting.FundMonthSummaryResponse;
import br.com.condominioauditoria.api.dto.response.accounting.FundMovementResponse;
import br.com.condominioauditoria.api.dto.response.accounting.FundMovementsResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualWarningResponse;
import br.com.condominioauditoria.api.model.accounting.FundBalance;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.enums.MonthStatus;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.CashFlowFile;
import br.com.condominioauditoria.api.util.MoneyFormatter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Every fund of every cash flow of the period, month by month (RF-13.5; ADR 0007, Decision 4). <b>Pure function</b>.
 * The split of credits and debits uses only the marks the system already has:
 * <ul>
 * <li>credits: transfer between funds → transfers received; fee receipt (Q25) → fee received; read without a fee
 * receipt → other income; without the mark (old read) → "outros créditos";</li>
 * <li>debits of the operating fund: as in the budget vs. actual of the month (lines, to reallocate and without budget
 * line = expenses; transfers = transfers sent; adjustments = "outros débitos"); without that calculation, transfers
 * between funds and "outros débitos";</li>
 * <li>debits of the other funds: transfers between funds → transfers sent; the rest → expenses.</li>
 * </ul>
 * Nothing is hidden: credits and debits always add up to the entries of the fund. Opening and closing balances are the
 * printed ones (Posição Financeira); the closing balance calculated from the entries is checked against the printed
 * one with a tolerance of R$ 0,01 (Q30). Month without a cash flow: null numbers, never zero.
 */
public final class FundMovements {

    public static final String VERSION = "1";
    public static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    /**
     * Debits of the operating fund in a month, as split by the budget vs. actual of that month.
     *
     * @param expenses lines + to reallocate + without budget line
     * @param transfers transfers between funds and accounts mapped to TRANSFER
     * @param adjustments accounts mapped to ADJUSTMENT
     */
    public record OperatingFundDebits(BigDecimal expenses, BigDecimal transfers, BigDecimal adjustments) {
    }

    /**
     * @param months months of the period, in order
     * @param cashFlows read cash flows (superseded partial ones already left out)
     * @param partialUntil partial cash flows: file id → last day with entries
     * @param balances printed fund positions of those files
     * @param entries entries of those files in the period
     * @param operatingDebits operating fund debits by month (from the budget vs. actual); missing = not calculated
     * @param plannedByMonth planned amount by month and fund: operating fund = lines 1.1 to 1.8; 1.9 funds = the line
     * @param fundLines 1.9 line linked to each fund (id and code)
     */
    public record Input(List<YearMonth> months, List<CashFlowFile> cashFlows, Map<UUID, LocalDate> partialUntil,
            List<FundBalance> balances, List<LedgerEntry> entries, Map<UUID, String> fundNames, UUID operatingFundId,
            Map<YearMonth, OperatingFundDebits> operatingDebits, Map<YearMonth, Map<UUID, BigDecimal>> plannedByMonth,
            Map<UUID, FundLine> fundLines) {

        public Input {
            months = List.copyOf(months);
            cashFlows = cashFlows == null ? List.of() : List.copyOf(cashFlows);
            partialUntil = partialUntil == null ? Map.of() : Map.copyOf(partialUntil);
            balances = balances == null ? List.of() : List.copyOf(balances);
            entries = entries == null ? List.of() : List.copyOf(entries);
            fundNames = fundNames == null ? Map.of() : Map.copyOf(fundNames);
            operatingDebits = operatingDebits == null ? Map.of() : Map.copyOf(operatingDebits);
            plannedByMonth = plannedByMonth == null ? Map.of() : Map.copyOf(plannedByMonth);
            fundLines = fundLines == null ? Map.of() : Map.copyOf(fundLines);
        }
    }

    public record FundLine(UUID lineId, String code) {
    }

    private FundMovements() {
    }

    public static FundMovementsResponse calculate(Input in) {
        List<BudgetVsActualWarningResponse> warnings = new ArrayList<>();
        List<FundMonthSummaryResponse> summaries = new ArrayList<>();
        // fund → month → movement
        Map<UUID, Map<YearMonth, FundMonthMovementResponse>> byFund = new LinkedHashMap<>();
        Set<UUID> seen = new LinkedHashSet<>();
        for (YearMonth m : in.months()) {
            List<CashFlowFile> files = in.cashFlows().stream().filter(f -> f.covers(m))
                    .sorted(Comparator.comparing(CashFlowFile::fileId)).toList();
            if (files.size() != 1) {
                MonthStatus status = files.isEmpty() ? MonthStatus.NO_CASH_FLOW : MonthStatus.TWO_CASH_FLOWS;
                if (status == MonthStatus.TWO_CASH_FLOWS) {
                    warnings.add(new BudgetVsActualWarningResponse("TWO_CASH_FLOWS", BudgetVsActualCalculator
                            .mmyyyy(m) + " com dois fluxos: substitua, reclassifique ou exclua um"));
                }
                summaries.add(new FundMonthSummaryResponse(m.toString(), status, null,
                        files.stream().map(CashFlowFile::used).toList(), 0, null));
                continue;
            }
            CashFlowFile file = files.getFirst();
            boolean singleMonth = YearMonth.from(file.periodStart()).equals(m)
                    && YearMonth.from(file.periodEnd()).equals(m);
            Map<UUID, FundBalance> balances = new LinkedHashMap<>();
            if (singleMonth) {
                in.balances().stream().filter(b -> b.getFileId().equals(file.fileId()))
                        .forEach(b -> balances.put(b.getFundId(), b));
            } else {
                warnings.add(new BudgetVsActualWarningResponse("CASH_FLOW_SPANS_MONTHS", "O extrato " + file.name()
                        + " cobre mais de um mês: saldos impressos do fundo não aparecem em "
                        + BudgetVsActualCalculator.mmyyyy(m)));
            }
            Map<UUID, Tally> tallies = new LinkedHashMap<>();
            balances.keySet().forEach(f -> tallies.put(f, new Tally()));
            in.entries().stream().filter(e -> e.getFileId().equals(file.fileId()) && YearMonth.from(e.getDate())
                    .equals(m)).sorted(Comparator.comparing(LedgerEntry::getDate).thenComparing(LedgerEntry::getPage)
                    .thenComparing(LedgerEntry::getSequence).thenComparing(LedgerEntry::getId))
                    .forEach(e -> tallies.computeIfAbsent(e.getFundId(), k -> new Tally()).add(e,
                            e.getFundId().equals(in.operatingFundId())));
            OperatingFundDebits op = in.operatingDebits().get(m);
            BigDecimal total = ZERO;
            boolean anyClosing = false;
            for (var t : tallies.entrySet()) {
                UUID fund = t.getKey();
                seen.add(fund);
                Tally tally = t.getValue();
                if (fund.equals(in.operatingFundId()) && op != null) {
                    tally.useOperatingSplit(op);
                }
                FundBalance b = balances.get(fund);
                BigDecimal opening = b == null ? null : b.getOpeningBalance();
                BigDecimal closing = b == null ? null : b.getClosingBalance();
                BigDecimal calculated = opening == null ? null : opening.add(tally.credits()).subtract(tally.debits());
                Boolean matches = calculated == null || closing == null ? null
                        : calculated.subtract(closing).abs().compareTo(TOLERANCE) <= 0;
                if (Boolean.FALSE.equals(matches)) {
                    warnings.add(new BudgetVsActualWarningResponse("FUND_BALANCE_MISMATCH", "Fundo "
                            + in.fundNames().getOrDefault(fund, fund.toString()) + ", "
                            + BudgetVsActualCalculator.mmyyyy(m) + ": saldo calculado difere do extrato (calculado R$ "
                            + MoneyFormatter.format(calculated) + "; extrato R$ " + MoneyFormatter.format(closing) + ")"));
                }
                if (closing != null) {
                    total = total.add(closing);
                    anyClosing = true;
                }
                BigDecimal planned = in.plannedByMonth().getOrDefault(m, Map.of()).get(fund);
                BigDecimal collected = planned != null && !fund.equals(in.operatingFundId()) ? tally.condoFee : null;
                byFund.computeIfAbsent(fund, k -> new LinkedHashMap<>()).put(m, new FundMonthMovementResponse(
                        m.toString(), true, opening, tally.condoFee, tally.otherIncome, tally.transfersIn,
                        tally.otherCredits, tally.credits(), tally.expenses, tally.transfersOut, tally.otherDebits,
                        tally.debits(), closing, b == null ? null : b.getCredits(), b == null ? null : b.getDebits(),
                        calculated, matches, planned, collected, tally.entries));
                if (tally.otherCredits.signum() != 0) {
                    warnings.add(new BudgetVsActualWarningResponse("UNCLASSIFIED_CREDITS", "Fundo "
                            + in.fundNames().getOrDefault(fund, fund.toString()) + ", "
                            + BudgetVsActualCalculator.mmyyyy(m) + ": R$ " + MoneyFormatter.format(tally.otherCredits)
                            + " em outros créditos (sem a marca de recebimento de cota: reprocesse o extrato)"));
                }
            }
            summaries.add(new FundMonthSummaryResponse(m.toString(), MonthStatus.WITH_CASH_FLOW,
                    in.partialUntil().get(file.fileId()), List.of(file.used()), tallies.size(),
                    anyClosing ? total : null));
        }

        List<UUID> order = new ArrayList<>(seen);
        order.sort(Comparator.comparing((UUID f) -> !f.equals(in.operatingFundId()))
                .thenComparing(f -> Objects.requireNonNullElse(in.fundNames().get(f), ""))
                .thenComparing(UUID::toString));
        List<FundMovementResponse> funds = new ArrayList<>();
        for (UUID fund : order) {
            List<FundMonthMovementResponse> months = new ArrayList<>();
            Map<YearMonth, FundMonthMovementResponse> ofFund = byFund.getOrDefault(fund, Map.of());
            for (YearMonth m : in.months()) {
                FundMonthMovementResponse r = ofFund.get(m);
                months.add(r != null ? r : new FundMonthMovementResponse(m.toString(), false, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null,
                        in.plannedByMonth().getOrDefault(m, Map.of()).get(fund), null, 0));
            }
            FundLine line = in.fundLines().get(fund);
            funds.add(new FundMovementResponse(fund, in.fundNames().get(fund), fund.equals(in.operatingFundId()),
                    line == null ? null : line.lineId(), line == null ? null : line.code(), List.copyOf(months)));
        }
        return new FundMovementsResponse(VERSION, in.months().isEmpty() ? null : in.months().getFirst().toString(),
                in.months().isEmpty() ? null : in.months().getLast().toString(), in.operatingFundId(),
                List.copyOf(summaries), List.copyOf(funds), List.copyOf(warnings));
    }

    /** Sums of one fund in one month (mutable only in here). */
    private static final class Tally {
        BigDecimal condoFee = ZERO;
        BigDecimal otherIncome = ZERO;
        BigDecimal transfersIn = ZERO;
        BigDecimal otherCredits = ZERO;
        BigDecimal expenses = ZERO;
        BigDecimal transfersOut = ZERO;
        BigDecimal otherDebits = ZERO;
        int entries;

        void add(LedgerEntry e, boolean operatingFund) {
            entries++;
            BigDecimal credit = e.getCredit();
            if (credit.signum() != 0) {
                if (e.isInterFundTransfer()) {
                    transfersIn = transfersIn.add(credit);
                } else if (e.getCondoFeeReceipt() == null) {
                    otherCredits = otherCredits.add(credit);
                } else if (e.getCondoFeeReceipt()) {
                    condoFee = condoFee.add(credit);
                } else {
                    otherIncome = otherIncome.add(credit);
                }
            }
            BigDecimal debit = e.getDebit();
            if (debit.signum() != 0) {
                if (e.isInterFundTransfer()) {
                    transfersOut = transfersOut.add(debit);
                } else if (operatingFund) {
                    // Without the budget vs. actual of the month, the operating fund's debits have no split
                    otherDebits = otherDebits.add(debit);
                } else {
                    expenses = expenses.add(debit);
                }
            }
        }

        /** Operating fund debits split as in the budget vs. actual; whatever it did not see stays in "outros". */
        void useOperatingSplit(OperatingFundDebits op) {
            BigDecimal all = debits();
            expenses = op.expenses();
            transfersOut = op.transfers();
            otherDebits = all.subtract(op.expenses()).subtract(op.transfers());
        }

        BigDecimal credits() {
            return condoFee.add(otherIncome).add(transfersIn).add(otherCredits);
        }

        BigDecimal debits() {
            return expenses.add(transfersOut).add(otherDebits);
        }
    }

    /** Planned amounts of a month by fund, from the budget valid in it. */
    public static Map<UUID, BigDecimal> planned(UUID operatingFundId, BigDecimal monthlyPlanned,
            Map<UUID, BigDecimal> byLinkedFund) {
        Map<UUID, BigDecimal> m = new HashMap<>(byLinkedFund);
        if (operatingFundId != null && monthlyPlanned != null) {
            m.put(operatingFundId, monthlyPlanned);
        }
        return m;
    }
}
