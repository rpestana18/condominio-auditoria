package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.MonthStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The bridge of one month (one column per month, RF-13.6). A partial month has its own column but is not in the total ({@code inTotal} false). */
public record BridgeMonthResponse(
        String month,
        MonthStatus status,
        LocalDate partialUntil,
        boolean inTotal,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal variation,
        List<BridgeMonthItemResponse> items) {
}
