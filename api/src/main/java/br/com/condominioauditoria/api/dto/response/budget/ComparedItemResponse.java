package br.com.condominioauditoria.api.dto.response.budget;

import java.util.List;
import java.util.UUID;

/** Budget item in the fiscal year comparison, with one value per fiscal year. */
public record ComparedItemResponse(
        UUID budgetItemId,
        String name,
        String group,
        List<ComparedValueResponse> values) {
}
