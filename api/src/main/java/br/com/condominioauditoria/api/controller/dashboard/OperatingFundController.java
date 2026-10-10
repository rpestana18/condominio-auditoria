package br.com.condominioauditoria.api.controller.dashboard;

import br.com.condominioauditoria.api.dto.request.dashboard.ConfirmOperatingFundRequest;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.condominium.OperatingFundService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Manager or the Admin confirms which fund is the condominium's operating fund (RF-05.1b). */
@RestController
@RequestMapping("/api/condominiums/{condominiumId}/operating-fund")
class OperatingFundController {

    private final CondominiumAccess access;
    private final OperatingFundService operatingFund;

    OperatingFundController(CondominiumAccess access, OperatingFundService operatingFund) {
        this.access = access;
        this.operatingFund = operatingFund;
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<Void> confirm(@PathVariable UUID condominiumId,
            @Valid @RequestBody ConfirmOperatingFundRequest request) {
        access.require(condominiumId);
        operatingFund.confirm(condominiumId, request.fundId(), access.username());
        return ResponseEntity.noContent().build();
    }
}
