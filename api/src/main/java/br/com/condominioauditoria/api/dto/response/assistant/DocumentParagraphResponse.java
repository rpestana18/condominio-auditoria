package br.com.condominioauditoria.api.dto.response.assistant;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** A paragraph of the answer and the numbers of the citations it relies on. */
public record DocumentParagraphResponse(
        @JsonProperty("texto") String text,
        @JsonProperty("citacoes") List<Integer> citations) {
}
