package br.com.condominioauditoria.api.dto.response.feature;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** costAvailable = false: the rag did not answer the price catalog; no cost value goes into the JSON. */
public record UsageResponse(
        @JsonProperty("condominioId") UUID condominiumId,
        @JsonProperty("inicio") LocalDate start,
        @JsonProperty("fim") LocalDate end,
        @JsonProperty("porFuncao") List<UsageTotalResponse> byFunction,
        @JsonProperty("porMes") List<UsageTotalResponse> byMonth,
        @JsonProperty("custoDisponivel") boolean costAvailable,
        @JsonProperty("custoEstimadoTotalUsd")
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = AbsentFilter.class)
        String estimatedTotalCostUsd,
        @JsonProperty("modelosSemPreco") List<String> modelsWithoutPrice) {
}
