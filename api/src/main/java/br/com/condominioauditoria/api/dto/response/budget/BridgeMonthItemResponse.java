package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BalanceBridgeItemCode;
import java.math.BigDecimal;

/** Amount of a bridge item in one month; null in a month in progress (partial month, Q50). */
public record BridgeMonthItemResponse(
        BalanceBridgeItemCode code,
        BigDecimal amount) {
}
