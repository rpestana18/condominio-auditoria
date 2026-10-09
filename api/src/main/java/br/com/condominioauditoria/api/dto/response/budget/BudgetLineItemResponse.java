package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetItemSource;
import br.com.condominioauditoria.api.model.enums.BudgetItemStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A budget line and its budget item. Null {@code status}: line without item. {@code account}: what the suggestion
 * compares (the budget's account, the account column text or, without both, the description).
 */
public record BudgetLineItemResponse(
        @JsonProperty("linhaId") UUID lineId,
        @JsonProperty("codigo") String code,
        @JsonProperty("grupo") String group,
        @JsonProperty("conta") String account,
        @JsonProperty("descricao") String description,
        @JsonProperty("orcado") BigDecimal budgeted,
        @JsonProperty("rubrica") BudgetItemResponse budgetItem,
        @JsonProperty("estado") BudgetItemStatus status,
        @JsonProperty("origem") BudgetItemSource source,
        @JsonProperty("motivo") String reason,
        @JsonProperty("atualizadoPor") String updatedBy,
        @JsonProperty("atualizadoEm") Instant updatedAt) {
}
