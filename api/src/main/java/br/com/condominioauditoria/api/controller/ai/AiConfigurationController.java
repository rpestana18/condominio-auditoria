package br.com.condominioauditoria.api.controller.ai;

import br.com.condominioauditoria.api.dto.request.ai.AiConfigurationRequest;
import br.com.condominioauditoria.api.dto.response.ai.AiConfigurationResponse;
import br.com.condominioauditoria.api.dto.response.ai.AiProviderResponse;
import br.com.condominioauditoria.api.exception.InvalidRequestException;
import br.com.condominioauditoria.api.mapper.AiConfigurationMapper;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.ai.AiCatalog;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService;
import br.com.condominioauditoria.api.service.condominium.CondominiumService;
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
 * The condominium's AI configuration and the provider catalog (RF-09.1, RF-09.2, RF-09.6). ADMIN only: User and
 * Manager neither see nor change it (the effective mode, without the key, goes out in the condominium context). The
 * key is write-only.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN')")
public class AiConfigurationController {

    private final AiConfigurationService service;
    private final AiCatalog catalog;
    private final CondominiumAccess access;
    private final CondominiumService condominiums;

    public AiConfigurationController(AiConfigurationService service, AiCatalog catalog, CondominiumAccess access,
            CondominiumService condominiums) {
        this.service = service;
        this.catalog = catalog;
        this.access = access;
        this.condominiums = condominiums;
    }

    /** Catalog for the screen fields, in the rag's order, without the public key. */
    @GetMapping("/ai/providers")
    public List<AiProviderResponse> providers() {
        return catalog.read(token()).providers().stream().map(AiConfigurationMapper::toResponse).toList();
    }

    @GetMapping("/condominiums/{condominiumId}/ai")
    public AiConfigurationResponse read(@PathVariable UUID condominiumId) {
        requireCondominium(condominiumId);
        return AiConfigurationMapper.toResponse(service.read(condominiumId));
    }

    @PutMapping("/condominiums/{condominiumId}/ai")
    public AiConfigurationResponse save(@PathVariable UUID condominiumId,
            @RequestBody(required = false) AiConfigurationRequest request) {
        requireCondominium(condominiumId);
        if (request == null || request.assistant() == null) {
            throw new InvalidRequestException("Informe o modo geral e a configuração do Assistente");
        }
        return AiConfigurationMapper.toResponse(service.save(condominiumId, AiConfigurationMapper.toChange(request),
                access.username(), token()));
    }

    private void requireCondominium(UUID condominiumId) {
        access.require(condominiumId);
        condominiums.requireExists(condominiumId);
    }

    private String token() {
        return access.bearerToken().orElseThrow(() -> new IllegalStateException("Token ausente"));
    }
}
