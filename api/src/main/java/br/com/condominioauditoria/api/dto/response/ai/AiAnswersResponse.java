package br.com.condominioauditoria.api.dto.response.ai;

import br.com.condominioauditoria.api.model.enums.AiMode;

/** The Assistant's answers (chat). Null mode = inherits the general mode. The key itself never comes back. */
public record AiAnswersResponse(
        AiMode mode,
        AiMode effectiveMode,
        String provider,
        String model,
        boolean keyRegistered,
        String keySuffix) {
}
