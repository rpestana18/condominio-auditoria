package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/** Budget item in the fiscal year comparison, with one value per fiscal year. */
public record ComparedItemResponse(
        @JsonProperty("rubricaId") UUID budgetItemId,
        @JsonProperty("nome") String name,
        @JsonProperty("grupo") String group,
        @JsonProperty("valores") List<ComparedValueResponse> values) {
}
