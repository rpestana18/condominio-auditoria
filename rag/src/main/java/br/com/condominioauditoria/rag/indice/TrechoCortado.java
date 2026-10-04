package br.com.condominioauditoria.rag.indice;

/** Pedaço de texto de um documento, pronto para o índice. Ordem a partir de 1, na ordem do documento. */
public record TrechoCortado(int ordem, Localizacao localizacao, String texto) {
}
