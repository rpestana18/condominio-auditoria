package br.com.condominioauditoria.api.dto.response.assistant;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One label and value of stored data quoted in the answer. */
public record DataRowResponse(@JsonProperty("rotulo") String label, @JsonProperty("valor") String value) {
}
