package br.com.condominioauditoria.api.controller.dashboard;

import br.com.condominioauditoria.api.dto.response.dashboard.DashboardResponse;
import br.com.condominioauditoria.api.dto.response.dashboard.MonthlyEvolutionResponse;
import br.com.condominioauditoria.api.exception.InvalidRequestException;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.dashboard.DashboardService;
import br.com.condominioauditoria.api.service.dashboard.MonthlyEvolutionService;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Numbers of the home screen. With no cash flow read yet, answers 204. */
@RestController
@RequestMapping("/api/condominiums/{condominiumId}/dashboard")
class DashboardController {

    private final CondominiumAccess access;
    private final DashboardService dashboard;
    private final MonthlyEvolutionService evolution;

    DashboardController(CondominiumAccess access, DashboardService dashboard, MonthlyEvolutionService evolution) {
        this.access = access;
        this.dashboard = dashboard;
        this.evolution = evolution;
    }

    @GetMapping
    ResponseEntity<DashboardResponse> dashboard(@PathVariable UUID condominiumId) {
        access.require(condominiumId);
        return dashboard.latest(condominiumId).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    /** Month-by-month evolution of the condo fee and the utilities (RF-12); {@code until} as YYYY-MM. */
    @GetMapping("/monthly-evolution")
    ResponseEntity<MonthlyEvolutionResponse> monthlyEvolution(@PathVariable UUID condominiumId,
            @RequestParam(name = "until", required = false) String until) {
        access.require(condominiumId);
        return evolution.evolution(condominiumId, until == null ? null : month(until)).map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    private static YearMonth month(String text) {
        try {
            return YearMonth.parse(text);
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException("Mês inválido: " + text + " (use AAAA-MM)");
        }
    }
}
