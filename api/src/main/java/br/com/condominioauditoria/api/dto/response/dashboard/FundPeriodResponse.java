package br.com.condominioauditoria.api.dto.response.dashboard;

import java.math.BigDecimal;
import java.util.UUID;

public record FundPeriodResponse(
        UUID fundId,
        String fund,
        BigDecimal openingBalance,
        BigDecimal inflows,
        BigDecimal outflows,
        BigDecimal result,
        BigDecimal closingBalance) {
}
