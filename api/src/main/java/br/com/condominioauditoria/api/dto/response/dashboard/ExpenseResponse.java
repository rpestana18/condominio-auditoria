package br.com.condominioauditoria.api.dto.response.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseResponse(
        LocalDate date,
        String fund,
        String account,
        String memo,
        BigDecimal amount,
        int page) {
}
