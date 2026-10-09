package br.com.condominioauditoria.api.dto.request.assistant;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Search by word in the documents (contracts/openapi.yaml, PedidoBuscaDocumentos). */
public record DocumentSearchRequest(
        @JsonProperty("texto") String text,
        @JsonProperty("filtros") DocumentFiltersRequest filters,
        @JsonProperty("limite") Integer limit) {
}
