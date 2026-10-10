package br.com.condominioauditoria.rag.exception;

/** Ollama did not answer, does not have the model or returned an unexpected vector. Readable message for the screen. */
public class EmbeddingsUnavailableException extends RuntimeException {

    public EmbeddingsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
