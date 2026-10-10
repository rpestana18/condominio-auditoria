package br.com.condominioauditoria.api.dto.response.budget;

import static br.com.condominioauditoria.api.util.MoneyFormatter.format;

import java.math.BigDecimal;

/** Difference per group between the previous uploaded budget and the printed column (warning, not a finding). */
public record GroupDifferenceResponse(
        String code,
        String description,
        BigDecimal uploadedBudget,
        BigDecimal printedColumn) {

    public String text() {
        return "A coluna \"Orçado anterior\" difere da PO anterior enviada no grupo " + code + " " + description
                + ": " + format(uploadedBudget) + " (PO enviada) × " + format(printedColumn) + " (coluna impressa)";
    }
}
