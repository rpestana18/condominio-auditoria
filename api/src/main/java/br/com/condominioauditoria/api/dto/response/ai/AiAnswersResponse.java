package br.com.condominioauditoria.api.dto.response.ai;

import br.com.condominioauditoria.api.model.enums.AiMode;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The Assistant's answers (chat). Null mode = inherits the general mode. The key itself never comes back. */
public record AiAnswersResponse(
        @JsonProperty("modo") AiMode mode,
        @JsonProperty("modoEfetivo") AiMode effectiveMode,
        @JsonProperty("provedor") String provider,
        @JsonProperty("modelo") String model,
        @JsonProperty("chaveCadastrada") boolean keyRegistered,
        @JsonProperty("chaveFinal") String keySuffix) {
}
