package br.com.condominioauditoria.rag.indice;

/** Onde o trecho está no original (ADR 0003, Q9). Um trecho nunca atravessa página, aba ou seção. */
public sealed interface Localizacao {

    /** PDF; página a partir de 1. */
    record Pagina(int numero) implements Localizacao {
    }

    /** Excel; linhas da planilha a partir de 1, inclusive. */
    record Planilha(String aba, int linhaInicio, int linhaFim) implements Localizacao {
    }

    /** Word; parágrafos a partir de 1, inclusive. Seção vazia até existir contracts/leitor/v2. */
    record Paragrafos(int inicio, int fim, String secao) implements Localizacao {
    }
}
