package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.SpendingProfile;
import java.math.BigDecimal;
import java.util.UUID;

/** Budget line in a bridge item: planned − actual of the period (positive = spent less than planned). */
public record BridgeLineResponse(
        UUID lineId,
        String code,
        String description,
        SpendingProfile effectiveProfile,
        BigDecimal amount) {
}
