package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Chart 5: lines furthest above and below planned. */
public record LargestDifferencesResponse(
        @JsonProperty("acima") List<LineDifferenceResponse> above,
        @JsonProperty("abaixo") List<LineDifferenceResponse> below) {
}
