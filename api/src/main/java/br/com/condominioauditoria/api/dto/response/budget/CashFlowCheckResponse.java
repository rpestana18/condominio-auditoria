package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;

/** Fund debits total = actual expense + adjustments + transfers (RF-03.1.6). */
public record CashFlowCheckResponse(
        BigDecimal fundDebits,
        int entries,
        BigDecimal actualExpense,
        BigDecimal adjustments,
        BigDecimal transfers,
        boolean matches) {
}
