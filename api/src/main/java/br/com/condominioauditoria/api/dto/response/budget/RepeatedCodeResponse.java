package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Printed code that repeats in the budget; resolved once the effective codes are distinct. */
public record RepeatedCodeResponse(
        @JsonProperty("codigoImpresso") String printedCode,
        @JsonProperty("resolvido") boolean resolved,
        @JsonProperty("linhas") List<RepeatedLineResponse> lines) {
}
