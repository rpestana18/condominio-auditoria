package br.com.condominioauditoria.api.dto.response.feature;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** costAvailable = false: the rag did not answer the price catalog; no cost value goes into the JSON. */
public record UsageResponse(
        UUID condominiumId,
        LocalDate start,
        LocalDate end,
        List<UsageTotalResponse> byFunction,
        List<UsageTotalResponse> byMonth,
        boolean costAvailable,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = AbsentFilter.class)
        String estimatedTotalCostUsd,
        List<String> modelsWithoutPrice) {
}
