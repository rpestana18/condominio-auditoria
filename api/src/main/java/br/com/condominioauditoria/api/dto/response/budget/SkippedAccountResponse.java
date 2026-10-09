package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Account skipped in a batch, with the reason. */
public record SkippedAccountResponse(@JsonProperty("conta") String account, @JsonProperty("motivo") String reason) {
}
