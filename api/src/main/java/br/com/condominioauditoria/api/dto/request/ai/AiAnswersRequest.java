package br.com.condominioauditoria.api.dto.request.ai;

import br.com.condominioauditoria.api.model.enums.AiMode;

/** The Assistant's answers. The key is write-only: null keeps the stored one; removeKey deletes it. */
public record AiAnswersRequest(
        AiMode mode,
        String provider,
        String model,
        String key,
        Boolean removeKey) {

    /** Never shows the key (not even in a Spring error log). */
    @Override
    public String toString() {
        return "AiAnswersRequest[" + mode + ", " + provider + "/" + model + ", chave "
                + (key == null ? "não enviada" : "enviada") + ", removerChave " + removeKey + "]";
    }
}
