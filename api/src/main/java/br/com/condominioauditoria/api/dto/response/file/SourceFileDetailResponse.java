package br.com.condominioauditoria.api.dto.response.file;

import java.util.List;

public record SourceFileDetailResponse(
        SourceFileResponse file,
        String sha256,
        List<TotalsCheckResponse> totalsChecks,
        List<FundBalanceResponse> funds) {
}
