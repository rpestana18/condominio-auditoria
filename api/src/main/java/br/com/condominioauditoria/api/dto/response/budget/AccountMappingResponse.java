package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.AccountMappingSource;
import br.com.condominioauditoria.api.model.enums.AccountMappingStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A cash flow account. Null {@code status}: account without mapping in this version. {@code ledgerEntries} and
 * {@code debits}: debits of the Condomínio fund in the budget's fiscal year (zero when the account only came from
 * the sheet).
 */
public record AccountMappingResponse(
        @JsonProperty("conta") String account,
        @JsonProperty("nome") String name,
        @JsonProperty("lancamentos") int ledgerEntries,
        @JsonProperty("debitos") BigDecimal debits,
        @JsonProperty("destino") MappingTargetResponse target,
        @JsonProperty("estado") AccountMappingStatus status,
        @JsonProperty("origem") AccountMappingSource source,
        @JsonProperty("motivo") String reason,
        @JsonProperty("igualVersaoAnterior") boolean sameAsPreviousVersion,
        @JsonProperty("atualizadoPor") String updatedBy,
        @JsonProperty("atualizadoEm") Instant updatedAt) {
}
