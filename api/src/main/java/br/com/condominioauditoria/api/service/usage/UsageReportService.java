package br.com.condominioauditoria.api.service.usage;

import br.com.condominioauditoria.api.dto.response.feature.UsageExportResponse;
import br.com.condominioauditoria.api.dto.response.feature.UsageResponse;
import br.com.condominioauditoria.api.mapper.UsageMapper;
import br.com.condominioauditoria.api.model.feature.ActivePeriod;
import br.com.condominioauditoria.api.report.UsageExcelReport;
import br.com.condominioauditoria.api.service.ai.AiCatalog;
import br.com.condominioauditoria.api.service.calculator.UsageCostCalculator.PeriodCost;
import br.com.condominioauditoria.api.service.condominium.CondominiumService;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import br.com.condominioauditoria.api.service.usage.UsageService.UsageSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Usage of the period on screen and in Excel (RF-10.6, RF-09.7), with the estimated cost in US$ (tokens × price of
 * the rag's catalog). Rag down: the usage still comes out, without cost.
 */
@Service
public class UsageReportService {

    private final UsageService usage;
    private final FeatureService features;
    private final CondominiumService condominiums;
    private final AiCatalog aiCatalog;

    public UsageReportService(UsageService usage, FeatureService features, CondominiumService condominiums,
            AiCatalog aiCatalog) {
        this.usage = usage;
        this.features = features;
        this.condominiums = condominiums;
        this.aiCatalog = aiCatalog;
    }

    /** @param bearerToken token of the calling user, passed on to the rag to read the price catalog */
    public UsageResponse usage(UUID condominiumId, LocalDate start, LocalDate end, Optional<String> bearerToken) {
        UsageSummary summary = usage.summary(condominiumId, start, end);
        return UsageMapper.toResponse(summary, cost(condominiumId, start, end, bearerToken));
    }

    /**
     * Active periods (that touch the period) and usage by month, in Excel with two sheets, with the estimated cost.
     * Rag down: the spreadsheet comes out without cost, with a warning.
     */
    public UsageExportResponse export(UUID condominiumId, LocalDate start, LocalDate end,
            Optional<String> bearerToken) {
        String name = condominiums.name(condominiumId);
        UsageSummary summary = usage.summary(condominiumId, start, end);
        Instant from = UsageService.startOfDay(start);
        Instant to = UsageService.startOfDay(end.plusDays(1));
        List<ActivePeriod> periods = features.catalog().features().stream()
                .flatMap(f -> features.periods(condominiumId, f.code()).stream())
                .filter(p -> p.overlaps(from, to))
                .toList();
        PeriodCost cost = cost(condominiumId, start, end, bearerToken);
        return new UsageExportResponse("uso-modulos-%s-a-%s.xlsx".formatted(start, end), UsageExcelReport.CONTENT_TYPE,
                UsageExcelReport.generate(name, summary, periods, cost));
    }

    /** Estimated cost of the period; null if the rag did not answer the price catalog. */
    private PeriodCost cost(UUID condominiumId, LocalDate start, LocalDate end, Optional<String> bearerToken) {
        return bearerToken.flatMap(aiCatalog::prices)
                .map(prices -> usage.cost(condominiumId, start, end, prices))
                .orElse(null);
    }
}
