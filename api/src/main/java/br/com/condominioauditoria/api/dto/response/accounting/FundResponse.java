package br.com.condominioauditoria.api.dto.response.accounting;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record FundResponse(UUID id, @JsonProperty("nome") String name, @JsonProperty("ordinario") boolean operating) {
}
