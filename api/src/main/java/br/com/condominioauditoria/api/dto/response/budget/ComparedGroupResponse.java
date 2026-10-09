package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Group in the fiscal year comparison, with one value per fiscal year. */
public record ComparedGroupResponse(
        @JsonProperty("codigo") String code,
        @JsonProperty("descricao") String description,
        @JsonProperty("fundos") boolean funds,
        @JsonProperty("valores") List<ComparedValueResponse> values) {
}
