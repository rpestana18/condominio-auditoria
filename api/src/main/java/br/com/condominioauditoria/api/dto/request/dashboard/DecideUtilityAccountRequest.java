package br.com.condominioauditoria.api.dto.request.dashboard;

import br.com.condominioauditoria.api.model.enums.UtilityType;

/** Decision on a cash flow account (RF-12.1): the utility it belongs to, or null when it is not a utility account. */
public record DecideUtilityAccountRequest(UtilityType utilityType) {
}
