package br.com.condominioauditoria.api.dto.response.budget;

import java.util.List;

/** Chart 5: lines furthest above and below planned. */
public record LargestDifferencesResponse(
        List<LineDifferenceResponse> above,
        List<LineDifferenceResponse> below) {
}
