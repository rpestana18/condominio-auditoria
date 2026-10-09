package br.com.condominioauditoria.api.exception;

/** Request with missing or inconsistent data (blank reason, inverted period). Becomes 400 in the API. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
