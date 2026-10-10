package br.com.condominioauditoria.api.dto.response.budget;

import java.util.UUID;

/** Line that makes up a value in the comparison, with its evidence target. */
public record UsedLineResponse(
        UUID lineId,
        String code,
        String description,
        String target) {
}
