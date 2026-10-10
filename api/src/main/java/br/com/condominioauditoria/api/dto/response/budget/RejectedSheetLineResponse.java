package br.com.condominioauditoria.api.dto.response.budget;


/** Sheet line not loaded: line number, content and reason. */
public record RejectedSheetLineResponse(
        int line,
        String content,
        String reason) {
}
