package br.com.condominioauditoria.api.dto.response.budget;


/**
 * Account counts of the mapping screen (Q54): linked by the system ({@code automatic}, low confidence included),
 * confirmed by the Admin, of low confidence (a part of the automatic ones), decided without a line ({@code rejected})
 * and without a link found ({@code withoutMapping}). The exceptions are {@code lowConfidence + withoutMapping}.
 */
public record AccountMappingSummaryResponse(
        int accounts,
        int automatic,
        int confirmed,
        int lowConfidence,
        int rejected,
        int withoutMapping) {
}
