package br.com.condominioauditoria.api.dto.request.budget;

import br.com.condominioauditoria.api.model.enums.AccountMappingBatchAction;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Confirm or reject the mapping of several accounts at once (RF-03.1.13). */
public record AccountMappingBatchRequest(
        @JsonProperty("acao") AccountMappingBatchAction action,
        @JsonProperty("contas") List<String> accounts) {
}
