package br.com.condominioauditoria.api.dto.request.budget;

import java.util.UUID;

/**
 * Budget item chosen by the Admin for a line: an existing one ({@code budgetItemId}) or a new one created from the line
 * ({@code newBudgetItem}, with the name). Null {@code confirm} = true.
 */
public record LineBudgetItemRequest(
        UUID budgetItemId,
        String newBudgetItem,
        Boolean confirm) {
}
