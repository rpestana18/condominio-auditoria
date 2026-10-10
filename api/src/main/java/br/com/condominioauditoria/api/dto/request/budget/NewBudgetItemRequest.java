package br.com.condominioauditoria.api.dto.request.budget;


/** New budget item in the catalog. Optional {@code group} (e.g. "1.3"). */
public record NewBudgetItemRequest(String name, String group) {
}
