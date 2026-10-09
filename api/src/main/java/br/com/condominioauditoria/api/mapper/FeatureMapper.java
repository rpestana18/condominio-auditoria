package br.com.condominioauditoria.api.mapper;

import br.com.condominioauditoria.api.config.properties.FeatureCatalog.FeatureDefinition;
import br.com.condominioauditoria.api.dto.response.feature.ActivePeriodResponse;
import br.com.condominioauditoria.api.dto.response.feature.FeatureEventResponse;
import br.com.condominioauditoria.api.dto.response.feature.FeatureResponse;
import br.com.condominioauditoria.api.model.feature.ActivePeriod;
import br.com.condominioauditoria.api.model.feature.FeatureEvent;
import br.com.condominioauditoria.api.service.feature.FeatureService.FeatureState;

/** Features, their activation trail and active periods as shown on the Features screen. */
public final class FeatureMapper {

    private FeatureMapper() {
    }

    public static FeatureResponse toResponse(FeatureState state) {
        FeatureDefinition d = state.definition();
        return new FeatureResponse(d.code(), d.name(), d.description(), d.includes(), d.dependsOn(),
                d.enabledByDefault(), state.enabled(), state.since(), state.catalogVersion());
    }

    public static FeatureEventResponse toResponse(FeatureEvent e) {
        return new FeatureEventResponse(e.getId(), e.getFeature(), e.isEnabledBefore(), e.isEnabledAfter(),
                e.getUsername(), e.getOccurredAt(), e.getReason());
    }

    public static ActivePeriodResponse toResponse(ActivePeriod p) {
        return new ActivePeriodResponse(p.feature(), p.start(), p.end(), p.enabledBy(), p.enableReason(),
                p.disabledBy(), p.disableReason());
    }
}
