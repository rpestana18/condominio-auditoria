package br.com.condominioauditoria.api.dto.request.budget;

/**
 * The Admin corrects the end of the fiscal year of a confirmed budget (RF-13.17): {@code fiscalYearEnd} as YYYY-MM,
 * with a required justification.
 */
public record FiscalYearEndRequest(
        String fiscalYearEnd,
        String justification) {
}
