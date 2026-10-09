package br.com.condominioauditoria.api.dto.response.budget;

import static br.com.condominioauditoria.api.util.MoneyFormatter.format;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Difference per group between the previous uploaded budget and the printed column (warning, not a finding). */
public record GroupDifferenceResponse(
        @JsonProperty("codigo") String code,
        @JsonProperty("descricao") String description,
        @JsonProperty("poEnviada") BigDecimal uploadedBudget,
        @JsonProperty("colunaImpressa") BigDecimal printedColumn) {

    public String text() {
        return "A coluna \"Orçado anterior\" difere da PO anterior enviada no grupo " + code + " " + description
                + ": " + format(uploadedBudget) + " (PO enviada) × " + format(printedColumn) + " (coluna impressa)";
    }
}
