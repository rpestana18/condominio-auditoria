package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.enums.BudgetLineMark;
import br.com.condominioauditoria.api.model.enums.BudgetLineType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/** Budget line as printed, with source file, page and hash (RF-03.1.1). */
public record BudgetLineResponse(
        UUID id,
        @JsonProperty("ordem") int position,
        @JsonProperty("pagina") int page,
        @JsonProperty("tipo") BudgetLineType type,
        @JsonProperty("codigoImpresso") String printedCode,
        @JsonProperty("codigoEfetivo") String effectiveCode,
        @JsonProperty("conta") String account,
        @JsonProperty("contaTexto") String accountText,
        @JsonProperty("marca") BudgetLineMark mark,
        @JsonProperty("descricao") String description,
        @JsonProperty("orcadoAnterior") BigDecimal previousBudgeted,
        @JsonProperty("orcado") BigDecimal budgeted,
        @JsonProperty("percentualTexto") String percentageText,
        @JsonProperty("observacoes") String notes,
        @JsonProperty("linhaDeFundo") boolean fundLine,
        @JsonProperty("arquivoId") UUID fileId,
        String sha256) {
}
