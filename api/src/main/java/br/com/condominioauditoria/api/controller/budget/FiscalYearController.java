package br.com.condominioauditoria.api.controller.budget;

import br.com.condominioauditoria.api.dto.response.budget.FiscalYearComparisonResponse;
import br.com.condominioauditoria.api.dto.response.budget.FiscalYearResponse;
import br.com.condominioauditoria.api.dto.response.budget.IndicatorsResponse;
import br.com.condominioauditoria.api.dto.response.budget.PrintedColumnCheckResponse;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.budget.FiscalYearComparisonService;
import br.com.condominioauditoria.api.service.budget.FiscalYearService;
import br.com.condominioauditoria.api.service.budget.IndicatorService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Análise da PO" menu: fiscal years, printed column check, comparison and indicators (RF-11.4 to RF-11.12). Every
 * role.
 */
@RestController
@RequestMapping("/api/condominios/{condominiumId}")
public class FiscalYearController {

    private final CondominiumAccess access;
    private final FiscalYearService service;
    private final FiscalYearComparisonService comparison;
    private final IndicatorService indicators;

    public FiscalYearController(CondominiumAccess access, FiscalYearService service,
            FiscalYearComparisonService comparison,
            IndicatorService indicators) {
        this.access = access;
        this.service = service;
        this.comparison = comparison;
        this.indicators = indicators;
    }

    @GetMapping("/exercicios")
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    public List<FiscalYearResponse> list(@PathVariable UUID condominiumId) {
        access.require(condominiumId);
        return service.list(condominiumId);
    }

    @GetMapping("/previsoes/{budgetId}/coluna-impressa")
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    public PrintedColumnCheckResponse printedColumn(@PathVariable UUID condominiumId, @PathVariable UUID budgetId) {
        access.require(condominiumId);
        return service.printedColumn(condominiumId, budgetId);
    }

    /** Compare fiscal years (RF-11.6): comma-separated {@code exercicios}; empty = the two most recent. */
    @GetMapping("/comparacao-exercicios")
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    public FiscalYearComparisonResponse compare(@PathVariable UUID condominiumId,
            @RequestParam(name = "exercicios", required = false) List<String> fiscalYears,
                    @RequestParam(name = "fundo", required = false) UUID fundId,
            @RequestParam(name = "mesmosMeses", defaultValue = "false") boolean sameMonths) {
        access.require(condominiumId);
        return comparison.compare(condominiumId, fiscalYears, fundId, sameMonths);
    }

    /** Indicators of a fiscal year (RF-11.10 to RF-11.12); empty {@code po} = the most recent. */
    @GetMapping("/indicadores")
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    public IndicatorsResponse indicators(@PathVariable UUID condominiumId, @RequestParam(name = "po",
            required = false) UUID budgetId,
            @RequestParam(name = "fundo", required = false) UUID fundId) {
        access.require(condominiumId);
        return indicators.indicators(condominiumId, budgetId, fundId);
    }
}
