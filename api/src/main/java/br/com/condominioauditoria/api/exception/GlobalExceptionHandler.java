package br.com.condominioauditoria.api.exception;

import br.com.condominioauditoria.api.orcamento.ConfirmacaoRecusadaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** API errors in the standard format (RFC 9457), with messages in Portuguese. */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateFileException.class)
    ProblemDetail duplicate(DuplicateFileException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        p.setTitle("Arquivo já enviado");
        p.setProperty("arquivoExistenteId", e.existingId());
        return p;
    }

    /** All reasons at once, so the screen shows what is missing. */
    @ExceptionHandler(ConfirmacaoRecusadaException.class)
    ProblemDetail confirmationRejected(ConfirmacaoRecusadaException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        p.setTitle("Confirmação recusada");
        p.setProperty("motivos", e.motivos());
        return p;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(MaxUploadSizeExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "O arquivo passa do limite de 50 MB");
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail invalidState(IllegalStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * Entry point of a feature disabled for the condominium (RF-10.3): "Módulo Assistente não contratado para este
     * condomínio."
     */
    @ExceptionHandler(FeatureNotEnabledException.class)
    ProblemDetail featureNotContracted(FeatureNotEnabledException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        p.setTitle("Módulo não contratado");
        p.setProperty("modulo", e.feature());
        return p;
    }

    @ExceptionHandler(UnknownFeatureException.class)
    ProblemDetail unknownFeature(UnknownFeatureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalidRequest(InvalidRequestException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** AI configuration rejected (RF-09.6): 422 with all the reasons. No reason carries the key. */
    @ExceptionHandler(AiConfigurationRejectedException.class)
    ProblemDetail aiConfigurationRejected(AiConfigurationRejectedException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
        p.setTitle("Configuração de IA recusada");
        p.setProperty("motivos", e.reasons());
        return p;
    }

    /** Rag down or without a public key while reading the catalog or saving the key: 503, nothing saved. */
    @ExceptionHandler(AiUnavailableException.class)
    ProblemDetail aiUnavailable(AiUnavailableException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        p.setTitle("Serviço de IA indisponível");
        return p;
    }

    /** Assistant chat and search: 409 by mode (with modoIa), 422, 429, 503 and 504 as in the contract. */
    @ExceptionHandler(AssistantRejectedException.class)
    ProblemDetail assistantRefusal(AssistantRejectedException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        p.setTitle(e.title());
        if (e.aiMode() != null) {
            p.setProperty("modoIa", e.aiMode().name());
        }
        return p;
    }
}
