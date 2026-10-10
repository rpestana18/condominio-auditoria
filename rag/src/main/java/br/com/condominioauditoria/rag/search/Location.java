package br.com.condominioauditoria.rag.search;

/** Where the chunk is in the original (ADR 0003, Q9). A chunk never spans pages, tabs or sections. */
public sealed interface Location {

    /** PDF; page from 1. */
    record Page(int number) implements Location {
    }

    /** Excel; sheet rows from 1, inclusive. */
    record Sheet(String tab, int startRow, int endRow) implements Location {
    }

    /** Word; paragraphs from 1, inclusive. Empty section until contracts/leitor/v2 exists. */
    record Paragraphs(int start, int end, String section) implements Location {
    }
}
