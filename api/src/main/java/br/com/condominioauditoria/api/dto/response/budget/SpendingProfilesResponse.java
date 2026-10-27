package br.com.condominioauditoria.api.dto.response.budget;

import java.util.List;
import java.util.UUID;

/** Spending profile of every expense line of a budget (RF-13.14); {@code suggestionChanged}: confirmed lines whose current suggestion differs (P9). */
public record SpendingProfilesResponse(
        UUID budgetId,
        Integer version,
        int confirmed,
        int suggested,
        int suggestionChanged,
        List<SpendingProfileLineResponse> lines) {
}
