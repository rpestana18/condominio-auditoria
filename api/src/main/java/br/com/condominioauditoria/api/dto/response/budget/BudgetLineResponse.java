package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetLineMark;
import br.com.condominioauditoria.api.model.enums.BudgetLineType;
import java.math.BigDecimal;
import java.util.UUID;

/** Budget line as printed, with source file, page and hash (RF-03.1.1). */
public record BudgetLineResponse(
        UUID id,
        int position,
        int page,
        BudgetLineType type,
        String printedCode,
        String effectiveCode,
        String account,
        String accountText,
        BudgetLineMark mark,
        String description,
        BigDecimal previousBudgeted,
        BigDecimal budgeted,
        String percentageText,
        String notes,
        boolean fundLine,
        UUID fileId,
        String sha256) {
}
