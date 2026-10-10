package br.com.condominioauditoria.api.controller.budget;

import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualResponse;
import br.com.condominioauditoria.api.dto.response.budget.EvidenceResponse;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.budget.BudgetVsActualExportService;
import br.com.condominioauditoria.api.service.budget.BudgetVsActualQueryService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Budget vs. actual (RF-03.1.6 to RF-03.1.12): every role of the condominium reads it. Calculated on each request by
 * the pure function {@link BudgetVsActualCalculator}; nothing is stored.
 */
@RestController
@RequestMapping("/api/condominiums/{condominiumId}/budget-vs-actual")
public class BudgetVsActualController {

    private final CondominiumAccess access;
    private final BudgetVsActualQueryService query;
    private final BudgetVsActualExportService exportService;

    public BudgetVsActualController(CondominiumAccess access, BudgetVsActualQueryService query,
            BudgetVsActualExportService export) {
        this.access = access;
        this.query = query;
        this.exportService = export;
    }

    /** PDF or Excel of the same view (RF-03.1.14): every role of the condominium exports it. */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    public ResponseEntity<byte[]> export(@PathVariable UUID condominiumId, @RequestParam("format") String format,
            @RequestParam("period") String period, @RequestParam(name = "budget", required = false) UUID budgetId,
            @RequestParam(name = "fund", required = false) UUID fundId) {
        access.require(condominiumId);
        String who = access.fullName().equals(access.username()) ? access.username()
                : access.fullName() + " (" + access.username() + ")";
        var file = exportService.export(condominiumId, period, budgetId, fundId, format, who);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.name())
                        .build().toString())
                .body(file.content());
    }

    /**
     * {@code period}: YYYY-MM or "cumulative". {@code budget}: budget version; without it, the one valid in the month.
     * {@code fund}: the operating fund (only the Condomínio fund) or another fund (only its panel); without it,
     * everything.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    public BudgetVsActualResponse get(@PathVariable UUID condominiumId, @RequestParam("period") String period,
            @RequestParam(name = "budget", required = false) UUID budgetId,
            @RequestParam(name = "fund", required = false) UUID fundId) {
        access.require(condominiumId);
        return fundId == null ? query.get(condominiumId, period, budgetId)
                : query.get(condominiumId, period, budgetId, fundId);
    }

    @GetMapping("/evidence")
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    public List<EvidenceResponse> evidence(@PathVariable UUID condominiumId, @RequestParam("period") String period,
            @RequestParam(name = "budget", required = false) UUID budgetId, @RequestParam("target") String target) {
        access.require(condominiumId);
        return query.evidence(condominiumId, period, budgetId, target);
    }
}
