package br.com.condominioauditoria.api.controller.accounting;

import br.com.condominioauditoria.api.dto.response.accounting.FundResponse;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.accounting.FundService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The condominium's funds, by the name printed in the cash flow (RF-03.1.9). Every role can read them. */
@RestController
@RequestMapping("/api/condominios/{condominiumId}/fundos")
class FundController {

    private final CondominiumAccess access;
    private final FundService funds;

    FundController(CondominiumAccess access, FundService funds) {
        this.access = access;
        this.funds = funds;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    List<FundResponse> list(@PathVariable UUID condominiumId) {
        access.require(condominiumId);
        return funds.list(condominiumId);
    }
}
