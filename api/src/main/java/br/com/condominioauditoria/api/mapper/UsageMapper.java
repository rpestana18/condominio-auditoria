package br.com.condominioauditoria.api.mapper;

import br.com.condominioauditoria.api.dto.response.feature.AbsentFilter;
import br.com.condominioauditoria.api.dto.response.feature.UsageResponse;
import br.com.condominioauditoria.api.dto.response.feature.UsageTotalResponse;
import br.com.condominioauditoria.api.model.usage.UsageTotal;
import br.com.condominioauditoria.api.service.calculator.UsageCostCalculator.PeriodCost;
import br.com.condominioauditoria.api.service.usage.UsageService.UsageSummary;
import java.math.BigDecimal;
import java.util.List;

/** Usage of the period, with the estimated cost in US$ when the price catalog answered (RF-09.7). */
public final class UsageMapper {

    private UsageMapper() {
    }

    /** cost null = price catalog unavailable: no cost value goes into the JSON. */
    public static UsageResponse toResponse(UsageSummary summary, PeriodCost cost) {
        return new UsageResponse(summary.condominiumId(), summary.start(), summary.end(),
                summary.byFunction().stream().map(t -> toResponse(t, rowCost(t, cost, false))).toList(),
                summary.byMonth().stream().map(t -> toResponse(t, rowCost(t, cost, true))).toList(),
                cost != null,
                cost == null ? AbsentFilter.ABSENT : text(cost.total()),
                cost == null ? List.of() : List.copyOf(cost.modelsWithoutPrice()));
    }

    private static UsageTotalResponse toResponse(UsageTotal t, String cost) {
        return new UsageTotalResponse(t.month(), t.feature(), t.function().code(), t.count(), t.inputTokens(),
                t.outputTokens(), t.files(), t.pages(), cost);
    }

    private static String rowCost(UsageTotal t, PeriodCost cost, boolean byMonth) {
        if (cost == null || (t.inputTokens() == 0 && t.outputTokens() == 0)) {
            return AbsentFilter.ABSENT;
        }
        return text(byMonth ? cost.ofMonth(t) : cost.ofFunction(t));
    }

    private static String text(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
