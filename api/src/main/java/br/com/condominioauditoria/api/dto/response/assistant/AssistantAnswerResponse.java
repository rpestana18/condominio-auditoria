package br.com.condominioauditoria.api.dto.response.assistant;

import br.com.condominioauditoria.api.model.enums.AnswerStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** The Assistant's answer (contracts/openapi.yaml, RespostaAssistente). */
public record AssistantAnswerResponse(
        @JsonProperty("situacao") AnswerStatus status,
        @JsonProperty("nosDocumentos") List<DocumentParagraphResponse> fromDocuments,
        @JsonProperty("nosDadosGravados") List<StoredDataResponse> fromStoredData,
        @JsonProperty("citacoes") List<DocumentCitationResponse> citations,
        @JsonProperty("sugestao") String suggestion,
        @JsonProperty("aviso") String warning,
        @JsonProperty("modelo") String model) {
}
