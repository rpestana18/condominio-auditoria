package br.com.condominioauditoria.api.dto.response.file;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SourceFileDetailResponse(
        @JsonProperty("arquivo") SourceFileResponse file,
        String sha256,
        @JsonProperty("conferencias") List<TotalsCheckResponse> totalsChecks,
        @JsonProperty("fundos") List<FundBalanceResponse> funds) {
}
