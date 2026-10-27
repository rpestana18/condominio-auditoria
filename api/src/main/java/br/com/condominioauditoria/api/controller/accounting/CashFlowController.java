package br.com.condominioauditoria.api.controller.accounting;

import br.com.condominioauditoria.api.dto.response.accounting.CashFlowEntriesResponse;
import br.com.condominioauditoria.api.dto.response.accounting.CashFlowMonthlyResponse;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.accounting.CashFlowService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** "Entradas e saídas" (RF-13.11 and RF-13.16; ADR 0009, Decision 4). Reading only, every role of the condominium. */
@RestController
@RequestMapping("/api/condominiums/{condominiumId}/cash-flow")
public class CashFlowController {

    private final CondominiumAccess access;
    private final CashFlowService service;

    public CashFlowController(CondominiumAccess access, CashFlowService service) {
        this.access = access;
        this.service = service;
    }

    @GetMapping("/monthly")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    public CashFlowMonthlyResponse monthly(@PathVariable UUID condominiumId,
            @RequestParam(required = false) String fund, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        access.require(condominiumId);
        return service.monthly(condominiumId, fund, from, to);
    }

    @GetMapping("/entries")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    public CashFlowEntriesResponse entries(@PathVariable UUID condominiumId,
            @RequestParam(required = false) String fund, @RequestParam(required = false) String month,
            @RequestParam(required = false) UUID line, @RequestParam(required = false) String block,
            @RequestParam(required = false) String account, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        access.require(condominiumId);
        return service.entries(condominiumId, fund, month, line, block, account, from, to);
    }
}
