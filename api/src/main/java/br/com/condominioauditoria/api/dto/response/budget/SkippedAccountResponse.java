package br.com.condominioauditoria.api.dto.response.budget;


/** Account skipped in a batch, with the reason. */
public record SkippedAccountResponse(String account, String reason) {
}
