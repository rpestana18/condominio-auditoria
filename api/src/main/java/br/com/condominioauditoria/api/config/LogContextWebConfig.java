package br.com.condominioauditoria.api.config;

import br.com.condominioauditoria.logging.LogContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The condominium of the route ({@code {condominiumId}}) in the MDC of every log line of the request (ADR 0008), the
 * error line of the central handler included (it runs before {@code afterCompletion}).
 */
@Configuration
class LogContextWebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CondominiumInterceptor());
    }

    static class CondominiumInterceptor implements HandlerInterceptor {

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables
                    && variables.get("condominiumId") instanceof String id && LogContext.isValid(id)) {
                MDC.put(LogContext.CONDOMINIUM_ID, id);
            }
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                Exception ex) {
            MDC.remove(LogContext.CONDOMINIUM_ID);
        }
    }
}
