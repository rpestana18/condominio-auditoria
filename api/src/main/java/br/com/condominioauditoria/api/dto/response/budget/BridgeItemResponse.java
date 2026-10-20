package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BalanceBridgeItemCode;
import java.math.BigDecimal;
import java.util.List;

/** Item of the bridge: amount, share of the variation in % (1 decimal; null with zero variation), parts (e.g. fee received and other income) and, for the line items, the lines that make it up (largest first). */
public record BridgeItemResponse(
        BalanceBridgeItemCode code,
        String label,
        BigDecimal amount,
        BigDecimal percentageOfVariation,
        List<BridgePartResponse> parts,
        List<BridgeLineResponse> lines) {
}
