package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Chart 4: actual of a group (1.1 to 1.8), month by month. */
public record GroupSeriesResponse(
        @JsonProperty("codigo") String code,
        @JsonProperty("descricao") String description,
        @JsonProperty("pontos") List<ValuePointResponse> points) {
}
