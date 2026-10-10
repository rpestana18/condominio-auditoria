package br.com.condominioauditoria.api.controller.audit;

import br.com.condominioauditoria.api.dto.response.audit.FindingResponse;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.audit.FindingQueryService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Findings of the condominium, read only, for every role (marking statuses comes with RF-02.8). */
@RestController
@RequestMapping("/api/condominiums/{condominiumId}/findings")
class FindingController {

    private final CondominiumAccess access;
    private final FindingQueryService findings;

    FindingController(CondominiumAccess access, FindingQueryService findings) {
        this.access = access;
        this.findings = findings;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USUARIO', 'GESTOR', 'ADMIN')")
    List<FindingResponse> list(@PathVariable UUID condominiumId,
            @RequestParam(name = "referenceMonth", required = false) String referenceMonth) {
        access.require(condominiumId);
        return findings.list(condominiumId, referenceMonth);
    }
}
