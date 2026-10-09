package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.service.calculator.BudgetReadingAssessment.CheckClassification;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A budget sum check as read by the rag, with the api's classification (RF-03.1.2). */
public record BudgetCheckResponse(
        @JsonProperty("codigo") String code,
        @JsonProperty("descricao") String description,
        boolean ok,
        @JsonProperty("detalhe") String detail,
        @JsonProperty("classificacao") CheckClassification classification,
        @JsonProperty("explicacao") String explanation) {
}
