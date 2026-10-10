package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.AccountMappingSource;
import br.com.condominioauditoria.api.model.enums.AccountMappingStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A cash flow account. Null {@code status}: account without mapping in this version. {@code ledgerEntries} and
 * {@code debits}: debits of the Condomínio fund in the budget's fiscal year (zero when the account only came from
 * the sheet).
 */
public record AccountMappingResponse(
        String account,
        String name,
        int ledgerEntries,
        BigDecimal debits,
        MappingTargetResponse target,
        AccountMappingStatus status,
        AccountMappingSource source,
        String reason,
        boolean sameAsPreviousVersion,
        String updatedBy,
        Instant updatedAt) {
}
