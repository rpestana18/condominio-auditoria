package br.com.condominioauditoria.api.controller.budget;

import br.com.condominioauditoria.api.dto.response.budget.BalanceBridgeResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetTrackingResponse;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.budget.BalanceBridgeService;
import br.com.condominioauditoria.api.service.budget.BudgetTrackingService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Acompanhamento da PO" (RF-13.4) and "Origem do saldo" (RF-13.6 and RF-13.7): every role of the condominium reads
 * them. Calculated on each request; nothing is stored.
 */
@RestController
@RequestMapping("/api/condominiums/{condominiumId}")
public class BudgetTrackingController {

    private final CondominiumAccess access;
    private final BudgetTrackingService tracking;
    private final BalanceBridgeService bridge;

    public BudgetTrackingController(CondominiumAccess access, BudgetTrackingService tracking,
            BalanceBridgeService bridge) {
        this.access = access;
        this.tracking = tracking;
        this.bridge = bridge;
    }

    /** {@code budget}: budget version; without it, the most recent confirmed one. */
    @GetMapping("/budget-tracking")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    public BudgetTrackingResponse grid(@PathVariable UUID condominiumId,
            @RequestParam(name = "budget", required = false) UUID budgetId) {
        access.require(condominiumId);
        return tracking.get(condominiumId, budgetId);
    }

    /** {@code fund}: fund id, "all" or empty (the operating fund); {@code from}/{@code to}: YYYY-MM. */
    @GetMapping("/balance-bridge")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    public BalanceBridgeResponse bridge(@PathVariable UUID condominiumId,
            @RequestParam(name = "fund", required = false) String fund,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {
        access.require(condominiumId);
        return bridge.get(condominiumId, fund, from, to);
    }
}
