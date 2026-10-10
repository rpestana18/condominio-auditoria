package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.service.calculator.BudgetReadingAssessment.CheckClassification;

/** A budget sum check as read by the rag, with the api's classification (RF-03.1.2). */
public record BudgetCheckResponse(
        String code,
        String description,
        boolean ok,
        String detail,
        CheckClassification classification,
        String explanation) {
}
