package br.com.condominioauditoria.api.dto.response.assistant;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A parameter of the query that produced stored data. */
public record QueryParameterResponse(@JsonProperty("nome") String name, @JsonProperty("valor") String value) {
}
