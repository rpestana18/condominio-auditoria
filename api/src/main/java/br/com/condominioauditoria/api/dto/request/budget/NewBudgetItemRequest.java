package br.com.condominioauditoria.api.dto.request.budget;

import com.fasterxml.jackson.annotation.JsonProperty;

/** New budget item in the catalog. Optional {@code group} (e.g. "1.3"). */
public record NewBudgetItemRequest(@JsonProperty("nome") String name, @JsonProperty("grupo") String group) {
}
