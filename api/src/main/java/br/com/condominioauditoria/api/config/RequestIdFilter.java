package br.com.condominioauditoria.api.config;

import br.com.condominioauditoria.logging.LogContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Request id of each REST request (ADR 0008): the {@code X-Request-Id} received from the screen (or a new one) goes to
 * the MDC of every log line of the request and back in the response header and in the error body ({@code requestId}),
 * so the user can tell support which line to look for. First filter of the chain, before the security.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = LogContext.acceptOrCreate(request.getHeader(LogContext.HTTP_HEADER));
        response.setHeader(LogContext.HTTP_HEADER, requestId);
        try (LogContext.Scope ignored = LogContext.with(LogContext.REQUEST_ID, requestId)) {
            chain.doFilter(request, response);
        }
    }

    /** The error dispatch (/error) runs in the same thread and keeps the id. */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }
}
