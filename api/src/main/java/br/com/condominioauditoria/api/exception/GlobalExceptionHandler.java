package br.com.condominioauditoria.api.exception;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * API errors in the standard format (RFC 9457), with messages in Portuguese. Every error answered by a controller
 * carries {@code detail}: the reason of a {@link ResponseStatusException} as written in the service, a Portuguese
 * text for the errors Spring MVC raises itself (missing parameter, invalid value, unreadable body...) and a generic
 * text for an unexpected error. Never a stack trace or the exception's own message for unexpected errors.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Portuguese titles of the statuses (Spring fills the English reason phrase). */
    private static final Map<Integer, String> TITLES = Map.ofEntries(
            Map.entry(400, "Requisição inválida"),
            Map.entry(401, "Não autenticado"),
            Map.entry(403, "Sem permissão"),
            Map.entry(404, "Não encontrado"),
            Map.entry(405, "Método não permitido"),
            Map.entry(406, "Formato de resposta não aceito"),
            Map.entry(409, "Conflito"),
            Map.entry(413, "Arquivo grande demais"),
            Map.entry(415, "Formato não suportado"),
            Map.entry(422, "Não processável"),
            Map.entry(429, "Muitas requisições"),
            Map.entry(500, "Erro interno"),
            Map.entry(503, "Serviço indisponível"),
            Map.entry(504, "Tempo esgotado"));

    /**
     * Errors that Spring MVC handles (including {@link ResponseStatusException}, which is an
     * {@code ErrorResponseException}): same status, with the title and the detail in Portuguese.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            translate(problem, ex);
        }
        return response;
    }

    /** {@code CondominiumAccess.require} and {@code @PreAuthorize}: 403 with the same text the screen shows. */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accessDenied(AccessDeniedException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Seu perfil não tem permissão para esta ação ou para este condomínio.");
        p.setTitle(TITLES.get(403));
        return p;
    }

    /** Unexpected error: logged with the stack trace here, answered with a generic text (nothing internal leaks). */
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unexpected error answering the request", e);
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno no servidor. Tente de novo; se continuar, avise o suporte.");
        p.setTitle(TITLES.get(500));
        return p;
    }

    static void translate(ProblemDetail problem, Exception ex) {
        String title = TITLES.get(problem.getStatus());
        if (title != null) {
            problem.setTitle(title);
        }
        if (ex instanceof ResponseStatusException rse && rse.getReason() != null && !rse.getReason().isBlank()) {
            problem.setDetail(rse.getReason());
        } else {
            problem.setDetail(detail(ex, problem.getStatus()));
        }
    }

    /** Portuguese text for the errors raised by Spring MVC itself (their default detail is in English). */
    static String detail(Exception ex, int status) {
        return switch (ex) {
            case MissingServletRequestParameterException e -> "Parâmetro obrigatório ausente: " + e.getParameterName();
            case MissingServletRequestPartException e -> "Parte obrigatória ausente no envio: " + e.getRequestPartName();
            case TypeMismatchException e -> e.getPropertyName() == null ? "Valor inválido na requisição"
                    : "Valor inválido para o parâmetro " + e.getPropertyName();
            case HttpMessageNotReadableException e -> "Corpo da requisição ausente ou mal formado";
            case MethodArgumentNotValidException e -> "Dados da requisição inválidos: " + String.join("; ",
                    e.getBindingResult().getFieldErrors().stream()
                            .map(f -> f.getField() + " " + f.getDefaultMessage()).toList());
            case HandlerMethodValidationException e -> "Parâmetros da requisição inválidos";
            case MaxUploadSizeExceededException e -> "O arquivo passa do limite de 50 MB";
            case NoResourceFoundException e -> "Endereço não encontrado na API";
            case HttpRequestMethodNotSupportedException e -> "Método " + e.getMethod() + " não permitido neste endereço";
            case HttpMediaTypeNotSupportedException e -> "Formato do corpo não suportado";
            default -> switch (status) {
                case 400 -> "Requisição inválida";
                case 404 -> "Não encontrado";
                case 403 -> "Seu perfil não tem permissão para esta ação.";
                case 409 -> "A operação conflita com o estado atual";
                case 503 -> "Serviço temporariamente indisponível";
                default -> "Erro ao atender a requisição (" + status + ")";
            };
        };
    }

    @ExceptionHandler(DuplicateFileException.class)
    ProblemDetail duplicate(DuplicateFileException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        p.setTitle("Arquivo já enviado");
        p.setProperty("existingFileId", e.existingId());
        return p;
    }

    /** All reasons at once, so the screen shows what is missing. */
    @ExceptionHandler(BudgetConfirmationRejectedException.class)
    ProblemDetail confirmationRejected(BudgetConfirmationRejectedException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        p.setTitle("Confirmação recusada");
        p.setProperty("reasons", e.reasons());
        return p;
    }

    /** The storage folder does not accept writes (e.g. owner of the mounted folder): 503, nothing saved. */
    @ExceptionHandler(StorageWriteDeniedException.class)
    ProblemDetail storageWriteDenied(StorageWriteDeniedException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        p.setTitle("Armazenamento indisponível");
        return p;
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
        p.setProperty("feature", e.feature());
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
        p.setProperty("reasons", e.reasons());
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
            p.setProperty("aiMode", e.aiMode().name());
        }
        return p;
    }
}
