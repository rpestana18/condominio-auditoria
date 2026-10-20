package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.CashFlowFile;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Partial cash flows (RF-13.10; ADR 0007, Decision 5 A). <b>Pure function</b>: a partial cash flow is replaced by a
 * complete one of the same month(s), so the month never becomes "dois fluxos para o mês". Two partial ones, or two
 * complete ones, of the same month are not chosen here: they stay "dois fluxos" (RF-03.1.10).
 *
 * @param effective cash flows used in the calculations (the superseded partial ones left out)
 * @param partialUntil partial cash flows among the effective ones: file id → last day with entries
 * @param superseded partial file id → id of the complete file that replaced it
 */
public record PartialCashFlows(List<CashFlowFile> effective, Map<UUID, LocalDate> partialUntil,
        Map<UUID, UUID> superseded) {

    /**
     * @param partialUntil every partial cash flow (file id → last day with entries); the others are complete
     */
    public static PartialCashFlows select(List<CashFlowFile> files, Map<UUID, LocalDate> partialUntil) {
        List<CashFlowFile> complete = files.stream().filter(f -> !partialUntil.containsKey(f.fileId()))
                .sorted(Comparator.comparing(CashFlowFile::fileId)).toList();
        List<CashFlowFile> effective = new ArrayList<>();
        Map<UUID, LocalDate> partial = new LinkedHashMap<>();
        Map<UUID, UUID> superseded = new LinkedHashMap<>();
        for (CashFlowFile f : files) {
            if (!partialUntil.containsKey(f.fileId())) {
                effective.add(f);
                continue;
            }
            List<YearMonth> months = months(f);
            CashFlowFile replacement = complete.stream()
                    .filter(c -> !months.isEmpty() && months.stream().allMatch(c::covers)).findFirst().orElse(null);
            if (replacement != null) {
                superseded.put(f.fileId(), replacement.fileId());
            } else {
                effective.add(f);
                partial.put(f.fileId(), partialUntil.get(f.fileId()));
            }
        }
        return new PartialCashFlows(List.copyOf(effective), Map.copyOf(partial), Map.copyOf(superseded));
    }

    static List<YearMonth> months(CashFlowFile f) {
        if (f.periodStart() == null || f.periodEnd() == null || f.periodEnd().isBefore(f.periodStart())) {
            return List.of();
        }
        return BudgetVsActualCalculator.months(YearMonth.from(f.periodStart()), YearMonth.from(f.periodEnd()));
    }
}
