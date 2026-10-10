package br.com.condominioauditoria.api.dto.response.budget;

import java.util.List;

/** Result of the suggestion sheet upload: accepted, skipped (already confirmed) and rejected lines. */
public record AccountMappingSheetResponse(
        int accepted,
        List<SkippedAccountResponse> skipped,
        List<RejectedSheetLineResponse> rejected) {
}
