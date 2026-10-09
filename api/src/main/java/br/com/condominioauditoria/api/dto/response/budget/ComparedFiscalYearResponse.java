package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.FiscalYearType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/** Compared fiscal year; {@code period} is the evidence period in budget vs. actual ("acumulado" or YYYY-MM). */
public record ComparedFiscalYearResponse(
        String id,
        @JsonProperty("tipo") FiscalYearType type,
        @JsonProperty("rotulo") String label,
        @JsonProperty("poId") UUID budgetId,
        @JsonProperty("versao") Integer version,
        @JsonProperty("inicio") String start,
        @JsonProperty("fim") String end,
        @JsonProperty("periodo") String period,
        @JsonProperty("meses") List<String> months) {
}
