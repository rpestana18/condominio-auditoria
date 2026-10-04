package br.com.condominioauditoria.rag.indice;

/** O Ollama não respondeu, não tem o modelo ou devolveu vetor fora do esperado. Mensagem legível para a tela. */
public class EmbeddingsIndisponiveisException extends RuntimeException {

    public EmbeddingsIndisponiveisException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
