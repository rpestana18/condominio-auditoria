package br.com.condominioauditoria.api.dto.response.assistant;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Stored data (numbers from the api's queries) quoted in the answer. */
public record StoredDataResponse(
        @JsonProperty("consulta") String query,
        @JsonProperty("parametros") List<QueryParameterResponse> parameters,
        @JsonProperty("linhas") List<DataRowResponse> rows,
        @JsonProperty("comentario") String comment) {
}
