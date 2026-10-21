package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BalanceBridgeItemCode;
import java.math.BigDecimal;
import java.util.List;

/**
 * Item of the bridge: amount, share of the variation in % (1 decimal; null with zero variation and in the
 * APPORTIONMENT_FUNDS item, Q49), parts (e.g. fee received and other income), for the line items the lines that make it
 * up (largest first) and, for APPORTIONMENT_FUNDS, the funds that make it up (largest first; empty in the others).
 */
public record BridgeItemResponse(
        BalanceBridgeItemCode code,
        String label,
        BigDecimal amount,
        BigDecimal percentageOfVariation,
        List<BridgePartResponse> parts,
        List<BridgeLineResponse> lines,
        List<BridgeFundResponse> funds) {
}
