package br.com.condominioauditoria.rag.model.budget;

import br.com.condominioauditoria.rag.model.enums.BudgetLineMark;
import br.com.condominioauditoria.rag.model.enums.BudgetLineType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * A budget line. {@code sequence} is the reading order and tells apart lines with the same printed code. "%" and
 * Observações stay as the text read and are not used in calculations.
 */
public record BudgetLine(
        @JsonProperty("ordem") int sequence,
        @JsonProperty("pagina") int page,
        @JsonProperty("tipo") BudgetLineType type,
        @JsonProperty("codigoImpresso") String printedCode,
        @JsonProperty("conta") String account,
        @JsonProperty("contaTexto") String accountText,
        @JsonProperty("marca") BudgetLineMark mark,
        @JsonProperty("descricao") String description,
        @JsonProperty("orcadoAnterior") BigDecimal previousBudgeted,
        @JsonProperty("orcado") BigDecimal budgeted,
        @JsonProperty("percentualTexto") String percentageText,
        @JsonProperty("observacoes") String notes) {
}
