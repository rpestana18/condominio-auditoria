package br.com.condominioauditoria.api.mapper;

import br.com.condominioauditoria.api.dto.response.budget.SpendingProfileEventResponse;
import br.com.condominioauditoria.api.dto.response.budget.SpendingProfileLineResponse;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.SpendingProfileEvent;
import br.com.condominioauditoria.api.model.enums.SpendingProfile;
import br.com.condominioauditoria.api.model.enums.SpendingProfileStatus;
import br.com.condominioauditoria.api.service.calculator.BudgetTrackingGrid.LineProfile;
import br.com.condominioauditoria.api.service.calculator.SpendingProfileSuggestion.Suggestion;

/** Spending profile of the budget lines (RF-13.14) → API DTOs (contracts/openapi.yaml). */
public final class SpendingProfileMapper {

    private SpendingProfileMapper() {
    }

    /**
     * The stored profile; without one, the current suggestion as SUGGESTED (it is calculated on the fly: V21 cleared
     * the stored suggestions so the revised rules hold).
     */
    public static LineProfile profile(BudgetLine line, Suggestion suggestion) {
        if (line.getSpendingProfile() != null) {
            return new LineProfile(line.getSpendingProfile(), line.getSpendingProfileStatus());
        }
        return new LineProfile(suggestion.profile(), SpendingProfileStatus.SUGGESTED);
    }

    public static SpendingProfileLineResponse toResponse(BudgetLine line, String groupCode, Suggestion suggestion) {
        LineProfile p = profile(line, suggestion);
        SpendingProfile effective = p.effective();
        // P9: a confirmed profile that the revised rules would suggest differently is shown, not hidden
        boolean suggestionChanged = p.status() == SpendingProfileStatus.CONFIRMED
                && p.profile() != suggestion.profile();
        return new SpendingProfileLineResponse(line.getId(), line.getPosition(), groupCode, line.getEffectiveCode(),
                line.getPrintedCode(), line.getDescription(), line.getAccount(), line.getMark(), line.getNotes(),
                line.getBudgeted(), p.profile(), p.status(), effective, suggestion.profile(), suggestion.reason(),
                suggestionChanged, line.getSpendingProfileReason(), line.getSpendingProfileChangedBy(),
                line.getSpendingProfileChangedAt());
    }

    public static SpendingProfileEventResponse toResponse(SpendingProfileEvent e) {
        return new SpendingProfileEventResponse(e.getId(), e.getBudgetLineId(), e.getLineCode(), e.getLineDescription(),
                e.getAction(), e.getUsername(), e.getOccurredAt(), e.getPreviousProfile(), e.getPreviousStatus(),
                e.getNewProfile(), e.getNewStatus(), e.getReason());
    }
}
