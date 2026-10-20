package br.com.condominioauditoria.api.dto.response.dashboard;

import java.util.List;

/**
 * Month-by-month evolution of the home screen (RF-12): the last 12 months up to {@code until}.
 *
 * @param condoFee null while the operating fund is not confirmed (RF-05.1b)
 * @param utilities one series per utility with a confirmed account; empty when none is confirmed
 * @param pendingUtilityAccounts accounts suggested as utilities and not decided yet (RF-12.1)
 */
public record MonthlyEvolutionResponse(
        List<String> months,
        String until,
        String operatingFund,
        MonthlySeriesResponse condoFee,
        List<MonthlySeriesResponse> utilities,
        int pendingUtilityAccounts) {
}
