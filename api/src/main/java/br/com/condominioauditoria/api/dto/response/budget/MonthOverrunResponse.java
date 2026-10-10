package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;

/** Overrun of a month in the fiscal year comparison, in R$ and as a % of planned. */
public record MonthOverrunResponse(
        String month,
        BigDecimal amount,
        BigDecimal percentage) {
}
