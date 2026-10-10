package br.com.condominioauditoria.api.dto.request.budget;

import java.util.UUID;

/** Fund line (1.9.x) of the budget linked to a cash flow fund; a null fund leaves the line without a fund. */
public record FundLinkRequest(UUID lineId, UUID fundId) {
}
