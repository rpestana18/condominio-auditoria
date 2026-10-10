package br.com.condominioauditoria.api.dto.request.feature;

import br.com.condominioauditoria.api.service.feature.FeatureService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Enable or disable a feature, with an optional reason (RF-10.2, RF-10.6). */
public record ChangeFeatureRequest(
        @NotNull(message = "Informe se o módulo fica ligado") Boolean enabled,
        @Size(max = FeatureService.MAX_REASON_LENGTH) String reason) {
}
