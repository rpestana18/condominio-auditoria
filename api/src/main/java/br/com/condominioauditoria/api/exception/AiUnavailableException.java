package br.com.condominioauditoria.api.exception;

/** The rag did not answer what the AI configuration needs (catalog or public key). Becomes 503 in the API. */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }
}
