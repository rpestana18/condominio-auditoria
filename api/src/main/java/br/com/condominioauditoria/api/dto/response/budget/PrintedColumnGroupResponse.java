package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A group of the column. {@code amount} = sum of the lines; {@code matches} = printed and sum within tolerance. */
public record PrintedColumnGroupResponse(
        UUID lineId,
        String code,
        String description,
        boolean funds,
        BigDecimal printed,
        BigDecimal amount,
        BigDecimal difference,
        boolean matches,
        List<PrintedColumnLineResponse> lines) {
}
