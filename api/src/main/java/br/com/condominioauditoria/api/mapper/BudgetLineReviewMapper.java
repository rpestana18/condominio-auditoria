package br.com.condominioauditoria.api.mapper;

import br.com.condominioauditoria.api.dto.response.budget.BudgetLineOriginResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetLineReviewEventResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetLineWarningResponse;
import br.com.condominioauditoria.api.dto.response.budget.LineReviewLineResponse;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.BudgetLineOrigin;
import br.com.condominioauditoria.api.model.budget.BudgetLineReviewEvent;
import br.com.condominioauditoria.api.service.calculator.BudgetLineReviewCheck;
import java.util.List;

/** Budget line review (RF-13.12) and line origin (RF-13.13) to their responses. */
public final class BudgetLineReviewMapper {

    private BudgetLineReviewMapper() {
    }

    public static LineReviewLineResponse toResponse(BudgetLine l, BudgetLine group, boolean funds,
            List<BudgetLineReviewCheck.Warning> warnings, BudgetLineOrigin origin) {
        return new LineReviewLineResponse(l.getId(), l.getPosition(), l.getPage(),
                group == null ? null : group.getPrintedCode(), group == null ? null : group.getDescription(), funds,
                l.getPrintedCode(), l.getEffectiveCode(), l.getAccount(), l.getMark(), l.getDescription(),
                l.getPreviousBudgeted(), l.getBudgeted(), l.getPercentageText(), l.getNotes(), l.getReviewStatus(),
                l.getReviewedBy(), l.getReviewedAt(), l.isGroupAccount(), l.isDescriptionPrevails(),
                warnings == null ? List.of() : warnings.stream()
                        .map(w -> new BudgetLineWarningResponse(w.code(), w.message())).toList(),
                origin == null ? null : toResponse(origin));
    }

    public static BudgetLineOriginResponse toResponse(BudgetLineOrigin o) {
        return new BudgetLineOriginResponse(o.getSourceAccounts(),
                o.getAverageFrom() == null ? null : o.getAverageFrom().toString(),
                o.getAverageTo() == null ? null : o.getAverageTo().toString(), o.getAverageAmount(),
                o.getBaseAmount(), o.getMethod(), o.getMethod() == null ? null : o.getMethod().label(),
                o.getIndexName(), o.getIndexRate(), o.isIndexForecast(), o.getIndexNote(), o.getFinalAmount(),
                o.getReason(), o.getSource(), o.getFileId(), o.getFileName(), o.getSheet(), o.getSheetRow(),
                o.getSha256(), o.getChangedBy(), o.getChangedAt());
    }

    public static BudgetLineReviewEventResponse toResponse(BudgetLineReviewEvent e) {
        return new BudgetLineReviewEventResponse(e.getId(), e.getBudgetLineId(), e.getLineCode(),
                e.getLineDescription(), e.getAction(), e.getUsername(), e.getOccurredAt(), e.getPreviousStatus(),
                e.getNewStatus(), e.isGroupAccount(), e.isDescriptionPrevails(), e.getReason());
    }
}
