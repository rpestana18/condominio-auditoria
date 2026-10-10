package br.com.condominioauditoria.rag.model;

/** Result of an arithmetic check. The detail explains the difference when there is one. */
public record TotalsCheck(
        String code,
        String description,
        boolean ok,
        String detail) {
}
