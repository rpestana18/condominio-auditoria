package br.com.condominioauditoria.api.dto.response.file;


public record TotalsCheckResponse(
        String code,
        String description,
        boolean ok,
        String detail) {
}
