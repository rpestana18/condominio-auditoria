package br.com.condominioauditoria.api.dto.response.budget;

import java.util.List;

/** Printed code that repeats in the budget; resolved once the effective codes are distinct. */
public record RepeatedCodeResponse(
        String printedCode,
        boolean resolved,
        List<RepeatedLineResponse> lines) {
}
