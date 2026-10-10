package br.com.condominioauditoria.api.dto.response.assistant;


/** One label and value of stored data quoted in the answer. */
public record DataRowResponse(String label, String value) {
}
