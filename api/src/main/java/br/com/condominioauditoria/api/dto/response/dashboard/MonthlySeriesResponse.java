package br.com.condominioauditoria.api.dto.response.dashboard;

import java.util.List;

/**
 * A monthly series of the home screen: the condo fee received ({@code key} CONDO_FEE) or a utility ({@code key} WATER,
 * ELECTRICITY or GAS).
 */
public record MonthlySeriesResponse(
        String key,
        String label,
        List<MonthlyPointResponse> points) {
}
