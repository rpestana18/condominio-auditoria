package br.com.condominioauditoria.api.dto.response.budget;

import java.util.List;
import java.util.UUID;

/** Chart 6: collection × planned of a fund linked to a 1.9 line. */
public record FundSeriesResponse(
        UUID fundId,
        String fund,
        String lineCode,
        List<FundPointResponse> points) {
}
