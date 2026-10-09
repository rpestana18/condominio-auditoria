package br.com.condominioauditoria.api.mapper;

import br.com.condominioauditoria.api.dto.response.budget.BudgetEventResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetExtensionResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetLineResponse;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetEvent;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.service.calculator.BudgetValidity;

/** Budget (PO) entities → budget API DTOs (contracts/openapi.yaml). */
public final class BudgetMapper {

    private BudgetMapper() {
    }

    /** The Admin's extension (RF-11.3); null when the budget was not extended. */
    public static BudgetExtensionResponse toExtensionResponse(Budget budget) {
        return BudgetValidity.extension(budget).map(v -> new BudgetExtensionResponse(v.start().toString(),
                v.end().toString(), budget.getExtensionJustification(), budget.getExtendedBy(),
                budget.getExtendedAt())).orElse(null);
    }

    public static BudgetLineResponse toResponse(BudgetLine line, boolean fundLine) {
        return new BudgetLineResponse(line.getId(), line.getPosition(), line.getPage(), line.getType(),
                line.getPrintedCode(), line.getEffectiveCode(), line.getAccount(), line.getAccountText(),
                line.getMark(), line.getDescription(), line.getPreviousBudgeted(), line.getBudgeted(),
                line.getPercentageText(), line.getNotes(), fundLine, line.getFileId(), line.getSha256());
    }

    public static BudgetEventResponse toResponse(BudgetEvent event) {
        return new BudgetEventResponse(event.getType(), event.getUsername(), event.getOccurredAt(),
                event.getJustification(), event.getDetail());
    }
}
