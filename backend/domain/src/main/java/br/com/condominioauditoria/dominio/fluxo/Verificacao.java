package br.com.condominioauditoria.dominio.fluxo;

/** Resultado de uma conferência aritmética. O detalhe explica a diferença quando há. */
public record Verificacao(String codigo, String descricao, boolean ok, String detalhe) {
}
