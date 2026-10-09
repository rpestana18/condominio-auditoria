package br.com.condominioauditoria.api.dto.response.file;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TotalsCheckResponse(
        @JsonProperty("codigo") String code,
        @JsonProperty("descricao") String description,
        boolean ok,
        @JsonProperty("detalhe") String detail) {
}
