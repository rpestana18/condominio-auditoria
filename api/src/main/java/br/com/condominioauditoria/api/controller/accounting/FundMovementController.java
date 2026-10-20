package br.com.condominioauditoria.api.controller.accounting;

import br.com.condominioauditoria.api.dto.response.accounting.FundMovementsResponse;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.accounting.FundMovementService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** "Fundos" (RF-13.5): every fund of the cash flows, month by month. Every role of the condominium reads it. */
@RestController
@RequestMapping("/api/condominiums/{condominiumId}/funds/movements")
public class FundMovementController {

    private final CondominiumAccess access;
    private final FundMovementService service;

    public FundMovementController(CondominiumAccess access, FundMovementService service) {
        this.access = access;
        this.service = service;
    }

    /** {@code from}/{@code to}: YYYY-MM; without them, the fiscal year of the most recent confirmed budget. */
    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    public FundMovementsResponse get(@PathVariable UUID condominiumId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {
        access.require(condominiumId);
        return service.get(condominiumId, from, to);
    }
}
