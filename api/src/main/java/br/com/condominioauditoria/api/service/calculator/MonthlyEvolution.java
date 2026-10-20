package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.dto.response.dashboard.MonthlyEntryResponse;
import br.com.condominioauditoria.api.dto.response.dashboard.MonthlyEvolutionResponse;
import br.com.condominioauditoria.api.dto.response.dashboard.MonthlyPointResponse;
import br.com.condominioauditoria.api.dto.response.dashboard.MonthlySeriesResponse;
import br.com.condominioauditoria.api.dto.response.dashboard.MonthlyVariationResponse;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.enums.MonthlyPointStatus;
import br.com.condominioauditoria.api.model.enums.UtilityType;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.CashFlowFile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Month-by-month evolution of the condo fee received and of the utility bills (RF-12). Pure function: the service
 * reads the cash flows, entries, operating fund and confirmed accounts, and this class only sums.
 *
 * <p>The month of each amount is the entry's date in the cash flow (Q18). A month is summed only when exactly one cash
 * flow covers it, as in the budget vs. actual (RF-03.1.10): no cash flow is "sem fluxo carregado", never zero. Each
 * variation compares two months that both have an amount; otherwise it is null ("sem base de comparação", RF-12.4).
 */
public final class MonthlyEvolution {

    public static final int MONTHS = 12;
    public static final String CONDO_FEE = "CONDO_FEE";
    private static final Pattern REFERENCE = Pattern.compile("REF\\.?\\s*(\\d{1,2})/(\\d{4})");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private MonthlyEvolution() {
    }

    /**
     * @param operatingFundId confirmed operating fund, or null (then there is no condo fee series)
     * @param utilityAccounts confirmed accounts and their utility; only these enter the utility series
     */
    public record Input(YearMonth until, List<CashFlowFile> cashFlows, List<LedgerEntry> entries,
            UUID operatingFundId, String operatingFundName, Map<UUID, String> fundNames,
            Map<String, UtilityType> utilityAccounts, int pendingUtilityAccounts) {
    }

    /** First month whose entries are needed: 12 months shown plus 12 before them for the previous-year comparison. */
    public static YearMonth firstNeeded(YearMonth until) {
        return until.minusMonths(2L * MONTHS - 1);
    }

    public static MonthlyEvolutionResponse calculate(Input in) {
        List<YearMonth> shown = new ArrayList<>();
        for (int i = MONTHS - 1; i >= 0; i--) {
            shown.add(in.until().minusMonths(i));
        }
        Map<YearMonth, List<CashFlowFile>> filesByMonth = new LinkedHashMap<>();
        for (YearMonth m = firstNeeded(in.until()); !m.isAfter(in.until()); m = m.plusMonths(1)) {
            YearMonth month = m;
            filesByMonth.put(month, in.cashFlows().stream().filter(f -> f.covers(month)).toList());
        }
        Map<UUID, String> fileNames = new LinkedHashMap<>();
        in.cashFlows().forEach(f -> fileNames.put(f.fileId(), f.name()));
        List<LedgerEntry> sorted = in.entries().stream()
                .sorted(Comparator.comparing(LedgerEntry::getDate).thenComparing(LedgerEntry::getPage)
                        .thenComparing(LedgerEntry::getSequence).thenComparing(LedgerEntry::getId))
                .toList();
        Context ctx = new Context(shown, filesByMonth, sorted, in.fundNames(), fileNames);

        MonthlySeriesResponse condoFee = null;
        if (in.operatingFundId() != null) {
            UUID fund = in.operatingFundId();
            condoFee = ctx.series(CONDO_FEE, "Cota ordinária recebida",
                    l -> l.getFundId().equals(fund) && !l.isInterFundTransfer()
                            && Boolean.TRUE.equals(l.getCondoFeeReceipt()) && l.getCredit().signum() != 0,
                    LedgerEntry::getCredit);
        }
        List<MonthlySeriesResponse> utilities = new ArrayList<>();
        EnumSet<UtilityType> confirmed = EnumSet.noneOf(UtilityType.class);
        confirmed.addAll(in.utilityAccounts().values());
        for (UtilityType type : confirmed) {
            utilities.add(ctx.series(type.name(), type.label(),
                    l -> l.getAccountCode() != null && type == in.utilityAccounts().get(l.getAccountCode())
                            && !l.isInterFundTransfer(),
                    l -> l.getDebit().subtract(l.getCredit())));
        }
        return new MonthlyEvolutionResponse(shown.stream().map(YearMonth::toString).toList(), in.until().toString(),
                in.operatingFundName(), condoFee, utilities, in.pendingUtilityAccounts());
    }

    /** Variation of {@code current} against {@code base}; null if either is missing. Percentage with one decimal. */
    public static MonthlyVariationResponse variation(BigDecimal current, BigDecimal base) {
        if (current == null || base == null) {
            return null;
        }
        BigDecimal amount = current.subtract(base).setScale(2, RoundingMode.HALF_UP);
        BigDecimal percentage = base.signum() == 0 ? null
                : amount.multiply(HUNDRED).divide(base.abs(), 10, RoundingMode.HALF_UP).setScale(1, RoundingMode.HALF_UP);
        return new MonthlyVariationResponse(amount, percentage);
    }

    /** Month of consumption written in the memo ("REF. 08/2026"), or null. */
    public static String referenceMonth(String memo) {
        if (memo == null) {
            return null;
        }
        Matcher m = REFERENCE.matcher(memo);
        if (!m.find()) {
            return null;
        }
        int month = Integer.parseInt(m.group(1));
        return month < 1 || month > 12 ? null : YearMonth.of(Integer.parseInt(m.group(2)), month).toString();
    }

    private record Month(MonthlyPointStatus status, BigDecimal amount, List<MonthlyEntryResponse> entries) {
    }

    private record Context(List<YearMonth> shown, Map<YearMonth, List<CashFlowFile>> filesByMonth,
            List<LedgerEntry> entries, Map<UUID, String> fundNames, Map<UUID, String> fileNames) {

        MonthlySeriesResponse series(String key, String label, Predicate<LedgerEntry> belongs,
                Function<LedgerEntry, BigDecimal> value) {
            Map<YearMonth, Month> months = new LinkedHashMap<>();
            filesByMonth.forEach((month, files) -> months.put(month, month(month, files, belongs, value)));
            List<MonthlyPointResponse> points = new ArrayList<>();
            for (YearMonth month : shown) {
                Month m = months.get(month);
                points.add(new MonthlyPointResponse(month.toString(), m.status(), m.amount(),
                        variation(m.amount(), months.get(month.minusMonths(1)).amount()),
                        variation(m.amount(), months.get(month.minusYears(1)).amount()), m.entries()));
            }
            return new MonthlySeriesResponse(key, label, points);
        }

        private Month month(YearMonth month, List<CashFlowFile> files, Predicate<LedgerEntry> belongs,
                Function<LedgerEntry, BigDecimal> value) {
            if (files.isEmpty()) {
                return new Month(MonthlyPointStatus.NO_CASH_FLOW, null, List.of());
            }
            if (files.size() > 1) {
                return new Month(MonthlyPointStatus.TWO_CASH_FLOWS, null, List.of());
            }
            UUID file = files.getFirst().fileId();
            BigDecimal total = BigDecimal.ZERO;
            List<MonthlyEntryResponse> used = new ArrayList<>();
            for (LedgerEntry l : entries) {
                if (!l.getFileId().equals(file) || !YearMonth.from(l.getDate()).equals(month) || !belongs.test(l)) {
                    continue;
                }
                BigDecimal amount = value.apply(l);
                total = total.add(amount);
                used.add(new MonthlyEntryResponse(l.getId(), l.getDate(), fundNames.get(l.getFundId()),
                        l.getAccountCode(), l.getAccountName(), l.getMemo(), amount, referenceMonth(l.getMemo()),
                        l.getFileId(), fileNames.get(l.getFileId()), l.getPage()));
            }
            if (used.isEmpty()) {
                return new Month(MonthlyPointStatus.NO_ENTRY, null, List.of());
            }
            return new Month(MonthlyPointStatus.LOADED, total.setScale(2, RoundingMode.HALF_UP), List.copyOf(used));
        }
    }
}
