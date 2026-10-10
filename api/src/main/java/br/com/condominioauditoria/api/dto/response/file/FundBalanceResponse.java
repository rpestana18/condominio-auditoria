package br.com.condominioauditoria.api.dto.response.file;

import java.math.BigDecimal;

public record FundBalanceResponse(
        String fund,
        BigDecimal openingBalance,
        BigDecimal credits,
        BigDecimal debits,
        BigDecimal closingBalance) {
}
