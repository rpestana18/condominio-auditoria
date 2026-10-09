package br.com.condominioauditoria.api.exception;

import java.util.List;

/** AI configuration rejected (422 in the API), with every reason at once. No reason carries the key. */
public class AiConfigurationRejectedException extends RuntimeException {

    private final List<String> reasons;

    public AiConfigurationRejectedException(List<String> reasons) {
        super(reasons.size() == 1 ? reasons.getFirst() : "Configuração de IA recusada: " + reasons.size() + " motivos");
        this.reasons = List.copyOf(reasons);
    }

    public List<String> reasons() {
        return reasons;
    }
}
