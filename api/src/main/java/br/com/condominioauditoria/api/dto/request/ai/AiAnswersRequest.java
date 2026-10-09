package br.com.condominioauditoria.api.dto.request.ai;

import br.com.condominioauditoria.api.model.enums.AiMode;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The Assistant's answers. The key is write-only: null keeps the stored one; removeKey deletes it. */
public record AiAnswersRequest(
        @JsonProperty("modo") AiMode mode,
        @JsonProperty("provedor") String provider,
        @JsonProperty("modelo") String model,
        @JsonProperty("chave") String key,
        @JsonProperty("removerChave") Boolean removeKey) {

    /** Never shows the key (not even in a Spring error log). */
    @Override
    public String toString() {
        return "AiAnswersRequest[" + mode + ", " + provider + "/" + model + ", chave "
                + (key == null ? "não enviada" : "enviada") + ", removerChave " + removeKey + "]";
    }
}
