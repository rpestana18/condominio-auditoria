package br.com.condominioauditoria.api.dto.response.assistant;

import java.util.List;

/** Stored data (numbers from the api's queries) quoted in the answer. */
public record StoredDataResponse(
        String query,
        List<QueryParameterResponse> parameters,
        List<DataRowResponse> rows,
        String comment) {
}
