package br.com.condominioauditoria.api.dto.response.budget;

import java.util.List;

/** Chart 4: actual of a group (1.1 to 1.8), month by month. */
public record GroupSeriesResponse(
        String code,
        String description,
        List<ValuePointResponse> points) {
}
