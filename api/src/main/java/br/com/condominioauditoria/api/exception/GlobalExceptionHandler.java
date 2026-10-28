package br.com.condominioauditoria.api.exception;

import br.com.condominioauditoria.logging.LogContext;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * API errors in the standard format (RFC 9457), with messages in Portuguese. Every error answered by a controller
 * carries {@code detail}: the reason of a {@link ResponseStatusException} as written in the service, a Portuguese
 * text for the errors Spring MVC raises itself (missing parameter, invalid value, unreadable body...) and a generic
 * text for an unexpected error. Never a stack trace or the exception's own message for unexpected errors.
 *
 * <p>The only place that logs the errors of the REST API (ADR 0008, D3: log once, at the edge): every 4xx is one WARN
 * line without stack ({@code GET /api/... → 400: detail}, with the route template, never the query string); every 5xx
 * is an ERROR with the stack, except 503 and 504, which are an expected dependency failure (WARN). Every error body
 * carries {@code requestId}, the same id of the log line and of the {@code X-Request-Id} header.
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
            logged(problem, ex);
        }
        return response;
    }

    /** {@code CondominiumAccess.require} and {@code @PreAuthorize}: 403 with the same text the screen shows. */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accessDenied(AccessDeniedException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Seu perfil não tem permissão para esta ação ou para este condomínio.");
        p.setTitle(TITLES.get(403));
        // Security event (A3): the log keeps the reason of the refusal ("Sem acesso a este condomínio"), the screen not
        return logged(p, e, e.getMessage());
    }

    /**
     * Unexpected error: logged with the stack trace here, answered with a generic text (nothing internal leaks) and the
     * request id, for the user to tell support.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        String requestId = LogContext.currentOrNewRequestId();
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno no servidor. Tente de novo; se continuar, avise o suporte informando o código "
                        + requestId + ".");
        p.setTitle(TITLES.get(500));
        p.setProperty("requestId", requestId);
        return logged(p, e);
    }

    /** Logs the error answered (once, here) and puts the request id in the body. */
    static ProblemDetail logged(ProblemDetail problem, Exception e) {
        return logged(problem, e, problem.getDetail());
    }

    private static ProblemDetail logged(ProblemDetail problem, Exception e, String reason) {
        if (problem.getProperties() == null || !problem.getProperties().containsKey("requestId")) {
            String requestId = LogContext.currentRequestId();
            if (requestId != null) {
                problem.setProperty("requestId", requestId);
            }
        }
        int status = problem.getStatus();
        String line = "{} → {}: {}";
        if (status >= 500 && status != 503 && status != 504) {
            log.error(line, route(), status, reason, e);
        } else {
            log.warn(line, route(), status, reason);
        }
        return problem;
    }

    /** "GET /api/condominios/{condominiumId}/po": method and route template, without ids or query string. */
    static String route() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            return "(fora de uma requisição)";
        }
        HttpServletRequest request = servlet.getRequest();
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return request.getMethod() + " " + (pattern != null ? pattern : "(rota não mapeada)");
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
        // The detail shows the original file name to the user; the log never does (LGPD, ADR 0008)
        return logged(p, e, "arquivo já enviado, igual ao arquivo " + e.existingId());
    }

    /** All reasons at once, so the screen shows what is missing. */
    @ExceptionHandler(BudgetConfirmationRejectedException.class)
    ProblemDetail confirmationRejected(BudgetConfirmationRejectedException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        p.setTitle("Confirmação recusada");
        p.setProperty("reasons", e.reasons());
        return logged(p, e);
    }

    /** The storage folder does not accept writes (e.g. owner of the mounted folder): 503, nothing saved. */
    @ExceptionHandler(StorageWriteDeniedException.class)
    ProblemDetail storageWriteDenied(StorageWriteDeniedException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        p.setTitle("Armazenamento indisponível");
        return logged(p, e);
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail invalidState(IllegalStateException e) {
        return logged(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage()), e);
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
        return logged(p, e);
    }

    @ExceptionHandler(UnknownFeatureException.class)
    ProblemDetail unknownFeature(UnknownFeatureException e) {
        return logged(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage()), e);
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalidRequest(InvalidRequestException e) {
        return logged(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage()), e);
    }

    /** AI configuration rejected (RF-09.6): 422 with all the reasons. No reason carries the key. */
    @ExceptionHandler(AiConfigurationRejectedException.class)
    ProblemDetail aiConfigurationRejected(AiConfigurationRejectedException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
        p.setTitle("Configuração de IA recusada");
        p.setProperty("reasons", e.reasons());
        return logged(p, e);
    }

    /** Rag down or without a public key while reading the catalog or saving the key: 503, nothing saved. */
    @ExceptionHandler(AiUnavailableException.class)
    ProblemDetail aiUnavailable(AiUnavailableException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        p.setTitle("Serviço de IA indisponível");
        return logged(p, e);
    }

    /** Assistant chat and search: 409 by mode (with modoIa), 422, 429, 503 and 504 as in the contract. */
    @ExceptionHandler(AssistantRejectedException.class)
    ProblemDetail assistantRefusal(AssistantRejectedException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        p.setTitle(e.title());
        if (e.aiMode() != null) {
            p.setProperty("aiMode", e.aiMode().name());
        }
        return logged(p, e);
    }
}
