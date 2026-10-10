package br.com.condominioauditoria.api.dto.request.assistant;


/** Search by word in the documents (contracts/openapi.yaml, DocumentSearchRequest). */
public record DocumentSearchRequest(
        String text,
        DocumentFiltersRequest filters,
        Integer limit) {
}
