package br.com.condominioauditoria.api.dto.response.dashboard;

import br.com.condominioauditoria.api.model.enums.UtilityAccountStatus;
import br.com.condominioauditoria.api.model.enums.UtilityType;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A cash flow account on the utility accounts screen (RF-12.1).
 *
 * @param suggested utility suggested by the account name, or null
 * @param utilityType confirmed utility; null unless the status is CONFIRMED
 * @param debits total of the account's real outflows in every cash flow read
 */
public record UtilityAccountResponse(
        String accountCode,
        String accountName,
        BigDecimal debits,
        UtilityAccountStatus status,
        UtilityType suggested,
        UtilityType utilityType,
        String decidedBy,
        Instant decidedAt) {
}
