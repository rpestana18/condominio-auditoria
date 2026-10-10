package br.com.condominioauditoria.api.dto.response.budget;

import java.util.List;

/** Group in the fiscal year comparison, with one value per fiscal year. */
public record ComparedGroupResponse(
        String code,
        String description,
        boolean funds,
        List<ComparedValueResponse> values) {
}
