package br.com.condominioauditoria.rag.search;

/** Piece of text of a document, ready for the index. Sequence from 1, in document order. */
public record Chunk(int sequence, Location location, String text) {
}
