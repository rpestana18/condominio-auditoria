package br.com.condominioauditoria.api.dto.response.budget;

import java.util.List;

/** Result of a mapping batch: how many accounts changed and the ones skipped, with the reason. */
public record AccountMappingBatchResponse(
        int changed,
        List<SkippedAccountResponse> skipped) {
}
