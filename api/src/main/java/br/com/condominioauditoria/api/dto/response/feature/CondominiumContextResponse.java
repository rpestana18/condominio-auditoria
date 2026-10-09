package br.com.condominioauditoria.api.dto.response.feature;

import br.com.condominioauditoria.api.ia.ConfiguracaoIaServico.ContextoAssistente;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/** Null assistant = Assistant feature disabled (contracts/openapi.yaml, ContextoCondominio). */
public record CondominiumContextResponse(
        @JsonProperty("condominioId") UUID condominiumId,
        @JsonProperty("nome") String name,
        @JsonProperty("modulosLigados") List<String> enabledFeatures,
        @JsonProperty("assistente") ContextoAssistente assistant) {
}
