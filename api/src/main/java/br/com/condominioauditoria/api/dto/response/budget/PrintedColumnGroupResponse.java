package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A group of the column. {@code amount} = sum of the lines; {@code matches} = printed and sum within tolerance. */
public record PrintedColumnGroupResponse(
        @JsonProperty("linhaId") UUID lineId,
        @JsonProperty("codigo") String code,
        @JsonProperty("descricao") String description,
        @JsonProperty("fundos") boolean funds,
        @JsonProperty("impresso") BigDecimal printed,
        @JsonProperty("valor") BigDecimal amount,
        @JsonProperty("diferenca") BigDecimal difference,
        @JsonProperty("confere") boolean matches,
        @JsonProperty("linhas") List<PrintedColumnLineResponse> lines) {
}
