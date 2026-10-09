package br.com.condominioauditoria.api.dto.request.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Fund line (1.9.x) of the budget linked to a cash flow fund; a null fund leaves the line without a fund. */
public record FundLinkRequest(@JsonProperty("linhaId") UUID lineId, @JsonProperty("fundoId") UUID fundId) {
}
