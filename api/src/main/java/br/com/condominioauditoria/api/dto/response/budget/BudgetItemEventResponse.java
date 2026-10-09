package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetItemAction;
import br.com.condominioauditoria.api.model.enums.BudgetItemSource;
import br.com.condominioauditoria.api.model.enums.BudgetItemStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/** An entry of the budget item trail, with the items by name. */
public record BudgetItemEventResponse(
        UUID id,
        @JsonProperty("linhaId") UUID lineId,
        @JsonProperty("codigo") String code,
        @JsonProperty("descricao") String description,
        @JsonProperty("acao") BudgetItemAction action,
        @JsonProperty("usuario") String username,
        @JsonProperty("em") Instant at,
        @JsonProperty("rubricaAnterior") String previousItem,
        @JsonProperty("estadoAnterior") BudgetItemStatus previousStatus,
        @JsonProperty("rubricaNova") String newItem,
        @JsonProperty("estadoNovo") BudgetItemStatus newStatus,
        @JsonProperty("origem") BudgetItemSource source,
        @JsonProperty("motivo") String reason) {
}
