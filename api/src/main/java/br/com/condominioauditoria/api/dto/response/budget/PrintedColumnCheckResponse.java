package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Check of a budget's "Orçado anterior" column (RF-11.5). {@code superseded}: there is a confirmed previous budget,
 * and the comparison uses it; {@code differences}: groups where the previous budget and the column differ beyond the
 * tolerance.
 */
public record PrintedColumnCheckResponse(
        String id,
        @JsonProperty("rotulo") String label,
        @JsonProperty("poId") UUID budgetId,
        @JsonProperty("substituida") boolean superseded,
        @JsonProperty("poAnteriorId") UUID previousBudgetId,
        @JsonProperty("poAnteriorRotulo") String previousBudgetLabel,
        @JsonProperty("totalImpresso") BigDecimal printedTotal,
        @JsonProperty("totalIncluiFundos") boolean totalIncludesFunds,
        @JsonProperty("fundos") BigDecimal funds,
        @JsonProperty("previstoMes") BigDecimal monthlyPlanned,
        @JsonProperty("grupos") List<PrintedColumnGroupResponse> groups,
        @JsonProperty("diferencas") List<GroupDifferenceResponse> differences,
        @JsonProperty("avisos") List<String> warnings) {
}
