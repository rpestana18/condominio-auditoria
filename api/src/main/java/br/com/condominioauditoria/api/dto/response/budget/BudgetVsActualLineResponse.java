package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetLineMark;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Budget line with planned, actual, difference and execution, and the cash flow accounts mapped to it. */
public record BudgetVsActualLineResponse(
        UUID lineId,
        String code,
        String description,
        String account,
        BudgetLineMark mark,
        String notes,
        int page,
        BigDecimal planned,
        BigDecimal actual,
        BigDecimal difference,
        BigDecimal execution,
        List<String> cashFlowAccounts,
        int entries) {
}
