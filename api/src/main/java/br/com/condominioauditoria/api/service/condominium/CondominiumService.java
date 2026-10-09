package br.com.condominioauditoria.api.service.condominium;

import br.com.condominioauditoria.api.dto.response.feature.CondominiumContextResponse;
import br.com.condominioauditoria.api.ia.ConfiguracaoIaServico;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** The condominium as the screen sees it: name, enabled features and the Assistant's AI mode. */
@Service
public class CondominiumService {

    private final CondominiumRepository condominiums;
    private final FeatureService features;
    private final ConfiguracaoIaServico aiConfig;

    public CondominiumService(CondominiumRepository condominiums, FeatureService features,
            ConfiguracaoIaServico aiConfig) {
        this.condominiums = condominiums;
        this.features = features;
        this.aiConfig = aiConfig;
    }

    /**
     * What the screen needs to build the menu: enabled features (RF-10.2, RF-10.3) and, with the Assistant enabled,
     * its effective AI mode (RF-04.16), without the key.
     */
    @Transactional(readOnly = true)
    public CondominiumContextResponse context(UUID condominiumId) {
        Condominium condominium = find(condominiumId);
        return new CondominiumContextResponse(condominium.getId(), condominium.getName(),
                features.enabledCodes(condominiumId), aiConfig.contexto(condominiumId));
    }

    /** The condominium's name; 404 if it does not exist. */
    @Transactional(readOnly = true)
    public String name(UUID condominiumId) {
        return find(condominiumId).getName();
    }

    private Condominium find(UUID condominiumId) {
        return condominiums.findById(condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
    }
}
