package br.com.condominioauditoria.api.dto.response.assistant;

import br.com.condominioauditoria.api.model.enums.AnswerStatus;
import java.util.List;

/** The Assistant's answer (contracts/openapi.yaml, AssistantAnswerResponse). */
public record AssistantAnswerResponse(
        AnswerStatus status,
        List<DocumentParagraphResponse> fromDocuments,
        List<StoredDataResponse> fromStoredData,
        List<DocumentCitationResponse> citations,
        String suggestion,
        String warning,
        String model) {
}
