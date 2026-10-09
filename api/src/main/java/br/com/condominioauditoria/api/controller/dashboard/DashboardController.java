package br.com.condominioauditoria.api.controller.dashboard;

import br.com.condominioauditoria.api.dto.response.dashboard.DashboardResponse;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.dashboard.DashboardService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Numbers of the home screen. With no cash flow read yet, answers 204. */
@RestController
@RequestMapping("/api/condominios/{condominiumId}/painel")
class DashboardController {

    private final CondominiumAccess access;
    private final DashboardService dashboard;

    DashboardController(CondominiumAccess access, DashboardService dashboard) {
        this.access = access;
        this.dashboard = dashboard;
    }

    @GetMapping
    ResponseEntity<DashboardResponse> dashboard(@PathVariable UUID condominiumId) {
        access.require(condominiumId);
        return dashboard.latest(condominiumId).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }
}
