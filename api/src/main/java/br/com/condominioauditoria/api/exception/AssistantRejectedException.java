package br.com.condominioauditoria.api.exception;

import br.com.condominioauditoria.api.model.enums.AiMode;
import org.springframework.http.HttpStatus;

/**
 * Question or search rejected or failed, already with the contract's HTTP status (409, 422, 429, 503, 504) and the
 * message in Portuguese for the screen. modoIa only on chat rejections by mode (409). Never carries a key or document
 * text.
 */
public class AssistantRejectedException extends RuntimeException {

    private final HttpStatus status;
    private final String title;
    private final AiMode aiMode;

    public AssistantRejectedException(HttpStatus status, String title, String message, AiMode aiMode) {
        super(message);
        this.status = status;
        this.title = title;
        this.aiMode = aiMode;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public AiMode aiMode() {
        return aiMode;
    }
}
