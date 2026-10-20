package br.com.condominioauditoria.api.controller.dashboard;

import br.com.condominioauditoria.api.dto.request.dashboard.DecideUtilityAccountRequest;
import br.com.condominioauditoria.api.dto.response.dashboard.UtilityAccountResponse;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.dashboard.UtilityAccountService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cash flow accounts of each utility (RF-12.1): everyone sees them, only the Manager or the Admin decides. */
@RestController
@RequestMapping("/api/condominiums/{condominiumId}/utility-accounts")
class UtilityAccountController {

    private final CondominiumAccess access;
    private final UtilityAccountService utilityAccounts;

    UtilityAccountController(CondominiumAccess access, UtilityAccountService utilityAccounts) {
        this.access = access;
        this.utilityAccounts = utilityAccounts;
    }

    @GetMapping
    List<UtilityAccountResponse> list(@PathVariable UUID condominiumId) {
        access.require(condominiumId);
        return utilityAccounts.list(condominiumId);
    }

    /** Confirms the account as a utility, or rejects it when {@code utilityType} is null. */
    @PutMapping("/{accountCode}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<Void> decide(@PathVariable UUID condominiumId, @PathVariable String accountCode,
            @RequestBody DecideUtilityAccountRequest request) {
        access.require(condominiumId);
        utilityAccounts.decide(condominiumId, accountCode, request.utilityType(), access.username());
        return ResponseEntity.noContent().build();
    }
}
