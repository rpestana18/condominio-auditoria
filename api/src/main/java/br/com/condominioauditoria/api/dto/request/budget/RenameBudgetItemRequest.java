package br.com.condominioauditoria.api.dto.request.budget;

import com.fasterxml.jackson.annotation.JsonProperty;

/** New name of a catalog budget item. */
public record RenameBudgetItemRequest(@JsonProperty("nome") String name) {
}
