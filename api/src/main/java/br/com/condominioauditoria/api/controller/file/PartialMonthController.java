package br.com.condominioauditoria.api.controller.file;

import br.com.condominioauditoria.api.dto.request.file.PartialMonthRequest;
import br.com.condominioauditoria.api.dto.response.file.PartialMonthEventResponse;
import br.com.condominioauditoria.api.dto.response.file.PartialMonthResponse;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.file.PartialMonthService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Partial month of the cash flows (RF-13.10; ADR 0007, Decision 5 A). Reading: every role of the condominium. Marking
 * or unmarking: the Manager or the Admin (Usuário gets 403), always with the trail.
 */
@RestController
@RequestMapping("/api/condominiums/{condominiumId}")
public class PartialMonthController {

    private final CondominiumAccess access;
    private final PartialMonthService service;

    public PartialMonthController(CondominiumAccess access, PartialMonthService service) {
        this.access = access;
        this.service = service;
    }

    @GetMapping("/partial-months")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    public List<PartialMonthResponse> list(@PathVariable UUID condominiumId) {
        access.require(condominiumId);
        return service.list(condominiumId);
    }

    @PutMapping("/files/{fileId}/partial-month")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public PartialMonthResponse mark(@PathVariable UUID condominiumId, @PathVariable UUID fileId,
            @RequestBody PartialMonthRequest request) {
        access.require(condominiumId);
        return service.mark(condominiumId, fileId, request, access.username());
    }

    @GetMapping("/files/{fileId}/partial-month/events")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    public List<PartialMonthEventResponse> events(@PathVariable UUID condominiumId, @PathVariable UUID fileId) {
        access.require(condominiumId);
        return service.events(condominiumId, fileId);
    }
}
