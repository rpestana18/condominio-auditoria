package br.com.condominioauditoria.api.security;

import br.com.condominioauditoria.logging.LogContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * User of the request in the MDC, right after the security (ADR 0008, D4): the Keycloak {@code sub}, a pseudonym,
 * never the username (LGPD). The audit trail with the name stays in the database.
 */
@Component
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1)
public class UserLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try (LogContext.Scope ignored = LogContext.with(LogContext.USER, subject(
                SecurityContextHolder.getContext().getAuthentication()))) {
            chain.doFilter(request, response);
        }
    }

    /** The token's {@code sub}; null without a token. */
    public static String subject(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof Jwt jwt ? jwt.getSubject() : null;
    }
}
