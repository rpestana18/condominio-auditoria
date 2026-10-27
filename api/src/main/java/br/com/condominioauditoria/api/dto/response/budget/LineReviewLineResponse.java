package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetLineMark;
import br.com.condominioauditoria.api.model.enums.BudgetLineReviewStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A budget line in the review (RF-13.12): the fields read from the document, the review state with who and when, the
 * Admin's marks (O1 {@code descriptionPrevails}, O2 {@code groupAccount}), the warnings and the origin (null = "origem
 * não informada"). {@code funds}: line of the 1.9 group.
 */
public record LineReviewLineResponse(
        UUID lineId,
        int position,
        int page,
        String groupCode,
        String groupDescription,
        boolean funds,
        String printedCode,
        String code,
        String account,
        BudgetLineMark mark,
        String description,
        BigDecimal previousBudgeted,
        BigDecimal budgeted,
        String percentageText,
        String notes,
        BudgetLineReviewStatus status,
        String reviewedBy,
        Instant reviewedAt,
        boolean groupAccount,
        boolean descriptionPrevails,
        List<BudgetLineWarningResponse> warnings,
        BudgetLineOriginResponse origin) {
}
