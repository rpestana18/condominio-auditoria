package br.com.condominioauditoria.api.controller.budget;

import br.com.condominioauditoria.api.dto.request.budget.BudgetConfirmationRequest;
import br.com.condominioauditoria.api.dto.request.budget.BudgetFundsRequest;
import br.com.condominioauditoria.api.dto.response.budget.BudgetDetailResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetEventResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetSummaryResponse;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.budget.BudgetConfirmationService;
import br.com.condominioauditoria.api.service.budget.BudgetFundLinkService;
import br.com.condominioauditoria.api.service.budget.BudgetQueryService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Read and confirmed budget (RF-03.1.1 to RF-03.1.3). Reading: every role of the condominium. Confirming: Admin. */
@RestController
@RequestMapping("/api/condominiums/{condominiumId}/budgets")
public class BudgetController {

    private final CondominiumAccess access;
    private final BudgetQueryService query;
    private final BudgetConfirmationService confirmation;
    private final BudgetFundLinkService fundLinks;

    public BudgetController(CondominiumAccess access, BudgetQueryService query,
            BudgetConfirmationService confirmation, BudgetFundLinkService fundLinks) {
        this.access = access;
        this.query = query;
        this.confirmation = confirmation;
        this.fundLinks = fundLinks;
    }

    /** RF-03.1.9: only the Admin changes the link of the 1.9 lines after confirmation; Gestor and Usuário get 403. */
    @PutMapping("/{budgetId}/funds")
    @PreAuthorize("hasRole('ADMIN')")
    public BudgetDetailResponse changeFunds(@PathVariable UUID condominiumId, @PathVariable UUID budgetId,
            @RequestBody BudgetFundsRequest request) {
        access.require(condominiumId);
        return fundLinks.change(condominiumId, budgetId, request, access.username());
    }

    /** The budget's audit trail (every role): confirmation, supersession and fund link changes. */
    @GetMapping("/{budgetId}/events")
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    public List<BudgetEventResponse> events(@PathVariable UUID condominiumId, @PathVariable UUID budgetId) {
        access.require(condominiumId);
        return query.events(condominiumId, budgetId).orElseThrow(BudgetController::notFound);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    public List<BudgetSummaryResponse> list(@PathVariable UUID condominiumId) {
        access.require(condominiumId);
        return query.list(condominiumId);
    }

    @GetMapping("/{budgetId}")
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    public BudgetDetailResponse detail(@PathVariable UUID condominiumId, @PathVariable UUID budgetId) {
        access.require(condominiumId);
        return query.detail(condominiumId, budgetId).orElseThrow(BudgetController::notFound);
    }

    /** RF-03.1.3: only the Admin confirms; Gestor and Usuário get 403. */
    @PostMapping("/{budgetId}/confirmation")
    @PreAuthorize("hasRole('ADMIN')")
    public BudgetDetailResponse confirm(@PathVariable UUID condominiumId, @PathVariable UUID budgetId,
            @RequestBody BudgetConfirmationRequest request) {
        access.require(condominiumId);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe os dados da confirmação");
        }
        return confirmation.confirm(condominiumId, budgetId, request, access.username());
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "PO não encontrada");
    }
}
