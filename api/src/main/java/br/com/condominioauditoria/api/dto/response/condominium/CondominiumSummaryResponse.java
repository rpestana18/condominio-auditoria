package br.com.condominioauditoria.api.dto.response.condominium;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record CondominiumSummaryResponse(UUID id, @JsonProperty("nome") String name) {
}
