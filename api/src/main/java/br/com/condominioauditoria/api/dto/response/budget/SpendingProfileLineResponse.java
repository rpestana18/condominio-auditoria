package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetLineMark;
import br.com.condominioauditoria.api.model.enums.SpendingProfile;
import br.com.condominioauditoria.api.model.enums.SpendingProfileStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Spending profile of a line: the stored (or suggested) profile and status, the profile in use ({@code effectiveProfile}: the confirmed one, or MONTHLY) and the current suggestion with its reason. */
public record SpendingProfileLineResponse(
        UUID lineId,
        int position,
        String groupCode,
        String code,
        String printedCode,
        String description,
        String account,
        BudgetLineMark mark,
        String notes,
        BigDecimal budgeted,
        SpendingProfile profile,
        SpendingProfileStatus status,
        SpendingProfile effectiveProfile,
        SpendingProfile suggestedProfile,
        String suggestionReason,
        String reason,
        String changedBy,
        Instant changedAt) {
}
