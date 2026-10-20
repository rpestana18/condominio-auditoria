package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;

/** Part of a bridge item (code, label and amount). */
public record BridgePartResponse(
        String code,
        String label,
        BigDecimal amount) {
}
