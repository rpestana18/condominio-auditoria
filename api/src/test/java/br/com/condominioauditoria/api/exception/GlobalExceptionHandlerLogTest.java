package br.com.condominioauditoria.api.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominioauditoria.api.config.RequestIdFilter;
import br.com.condominioauditoria.logging.LogContext;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/**
 * ADR 0008, D3: the central handler logs every error once. 4xx = one WARN line with the route template and the reason,
 * without stack; 5xx = ERROR with the stack; 503 = WARN. The request id of the X-Request-Id header goes back in the
 * response header and in the error body (requestId).
 */
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerLogTest {

    /** @Controller + @ResponseBody, not @RestController: the contract snapshot only reads the real controllers. */
    @Controller
    @ResponseBody
    static class FailingController {

        @GetMapping("/api/condominiums/{condominiumId}/budget-vs-actual")
        String badPeriod(@PathVariable UUID condominiumId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período deve ser AAAA-MM");
        }

        @GetMapping("/api/condominiums/{condominiumId}/outro")
        String otherCondominium(@PathVariable UUID condominiumId) {
            throw new AccessDeniedException("Sem acesso a este condomínio");
        }

        @GetMapping("/api/bug")
        String bug() {
            throw new IllegalArgumentException("No enum constant X");
        }

        @GetMapping("/api/ia")
        String aiDown() {
            throw new AiUnavailableException("O serviço de IA não respondeu");
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestIdFilter()).build();
    }

    @Test
    void a400IsOneWarnLineWithRouteReasonAndRequestIdWithoutStack(CapturedOutput output) throws Exception {
        UUID condominium = UUID.randomUUID();
        mvc.perform(get("/api/condominiums/" + condominium + "/budget-vs-actual?from=2026-13")
                        .header(LogContext.HTTP_HEADER, "req-400"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(LogContext.HTTP_HEADER, "req-400"))
                .andExpect(jsonPath("$.requestId").value("req-400"))
                .andExpect(jsonPath("$.detail").value("Período deve ser AAAA-MM"));

        String line = output.getOut().lines().filter(l -> l.contains("→ 400")).findFirst().orElseThrow();
        assertThat(line).contains("WARN")
                .contains("GET /api/condominiums/{condominiumId}/budget-vs-actual → 400: Período deve ser AAAA-MM")
                .doesNotContain(condominium.toString()).doesNotContain("2026-13");
        assertThat(output.getOut()).doesNotContain("\tat ").doesNotContain("ERROR");
    }

    @Test
    void a403OfAnotherCondominiumIsAWarnWithTheReason(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/condominiums/" + UUID.randomUUID() + "/outro")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        assertThat(output.getOut()).contains("WARN").contains("→ 403: Sem acesso a este condomínio")
                .doesNotContain("\tat ");
    }

    @Test
    void a500IsAnErrorWithTheStackAndTheIdForSupport(CapturedOutput output) throws Exception {
        MvcResult result = mvc.perform(get("/api/bug")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.requestId").isNotEmpty()).andReturn();

        String requestId = result.getResponse().getHeader(LogContext.HTTP_HEADER);
        assertThat(LogContext.isValid(requestId)).isTrue();
        assertThat(result.getResponse().getContentAsString()).contains(requestId).doesNotContain("No enum constant");
        assertThat(output.getOut()).contains("ERROR").contains("GET /api/bug → 500")
                .contains("java.lang.IllegalArgumentException: No enum constant X").contains("\tat ");
    }

    @Test
    void a503OfADependencyIsAWarnWithoutStack(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/ia")).andExpect(status().isServiceUnavailable());

        assertThat(output.getOut()).contains("WARN").contains("GET /api/ia → 503").doesNotContain("\tat ")
                .doesNotContain("ERROR");
    }

    @Test
    void anInvalidRequestIdIsReplaced() throws Exception {
        MvcResult result = mvc.perform(get("/api/ia").header(LogContext.HTTP_HEADER, "linha\nforjada"))
                .andReturn();

        assertThat(result.getResponse().getHeader(LogContext.HTTP_HEADER)).isNotEqualTo("linha\nforjada")
                .matches("[A-Za-z0-9._-]+");
    }
}
