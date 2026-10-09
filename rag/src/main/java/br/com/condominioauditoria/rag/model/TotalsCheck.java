package br.com.condominioauditoria.rag.model;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Result of an arithmetic check. The detail explains the difference when there is one. */
public record TotalsCheck(
        @JsonProperty("codigo") String code,
        @JsonProperty("descricao") String description,
        boolean ok,
        @JsonProperty("detalhe") String detail) {
}
