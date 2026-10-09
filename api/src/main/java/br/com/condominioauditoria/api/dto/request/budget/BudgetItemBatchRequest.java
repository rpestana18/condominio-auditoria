package br.com.condominioauditoria.api.dto.request.budget;

import br.com.condominioauditoria.api.model.enums.BudgetItemBatchAction;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/** Confirm or reject the budget item of several lines at once. */
public record BudgetItemBatchRequest(
        @JsonProperty("acao") BudgetItemBatchAction action,
        @JsonProperty("linhas") List<UUID> lines) {
}
